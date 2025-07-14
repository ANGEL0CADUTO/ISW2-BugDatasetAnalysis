// in src/main/java/org/example/Main.java
package org.example;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.example.config.ProjectConfig;
import org.example.logic.BugginessLogic;
import org.example.logic.HistoryAnalyzer;
import org.example.logic.MetricsLogic;
import org.example.model.*;
import org.example.services.CsvWriterService;
import org.example.services.GitService;
import org.example.services.JiraService;

import org.example.services.PmdAnalyzer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Main {

    // --- CONTATORI PER IL DEBUG DEI RENAME ---
    public static final AtomicInteger renameCount = new AtomicInteger(0);
    public static final AtomicInteger totalDiffCount = new AtomicInteger(0);
    // -----------------------------------------

    public static void main(String[] args) {



        // --- CONFIGURAZIONE CENTRALIZZATA ---
        ProjectConfig bookkeeperConfig = new ProjectConfig(
                "BOOKKEEPER",
                "C:/Users/aroma/IdeaProjects/bookkeeper",
                "./bookkeeper_dataset.csv"
        );

        ProjectConfig avroConfig = new ProjectConfig(
                "AVRO",
                "C:/Users/aroma/IdeaProjects/avro",
                "./avro_dataset.csv"
        );




        //BOOKKEEPER
        new Main().run(bookkeeperConfig);

        //AVRO
        new Main().run(avroConfig);


    }

    public void run(ProjectConfig config) {
        long totalStartTime = System.currentTimeMillis();
        System.out.println("Avvio generazione dataset per il progetto: " + config.getProjectName());

        GitService gitService = null;
        JiraService jiraService = new JiraService();
        CsvWriterService csvWriter = null;
        PmdAnalyzer pmdAnalyzer = new PmdAnalyzer();

        try {
            // Usa i path dalla configurazione
            Path repoPath = Paths.get(config.getRepoPath());
            gitService = new GitService(repoPath);
            csvWriter = new CsvWriterService(config.getOutputCsvPath());

            csvWriter.writeHeader("ProjectName", "MethodID", "ReleaseID",
                    "LOC", "CC", "ParamCount", "NestingDepth", "NSmells",
                    "NR", "NAuth", "Churn", "MaxChurn", "NFix", "AvgChurn",
                    "ClassNR", "ClassNAuth", "ClassChurn","AvgClassChurn","Bugginess");
            List<Release> releases = getReleases(gitService);
            List<JiraTicket> tickets = jiraService.getFixedBugTickets(config.getProjectName());


            BugginessLogic bugginessLogic = new BugginessLogic(releases, null);
            bugginessLogic.calculateBugLifecycles(tickets);

            Set<String> ticketKeys = tickets.stream().map(JiraTicket::getKey).collect(Collectors.toSet());
            Map<String, RevCommit> bugCommits = gitService.linkBugsToCommits(ticketKeys);

            HistoryAnalyzer historyAnalyzer = new HistoryAnalyzer(gitService);
            HistoryAnalyzer.AnalysisResult analysisResult = historyAnalyzer.analyzeCommitHistory(bugCommits);
            Map<String, MethodHistory> methodsHistories = analysisResult.methodHistories;
            Map<String, FileHistory> fileHistories = analysisResult.fileHistories;
            MetricsLogic metricsLogic = new MetricsLogic();
            BugginessLogic finalBugginessLogic = new BugginessLogic(releases, methodsHistories);
            List<Release> consideredReleases = filterReleases(releases);

            System.out.println("Inizio analisi per release e generazione CSV...");

            for (Release currentRelease : consideredReleases) {
                System.out.printf("\n--- Processando release %s ---\n", currentRelease.getName());

                Path releaseTempDir = null;
                Map<String, Integer> smellsMap = Collections.emptyMap();
                try {
                    // --- 1. CREA DIR TEMPORANEA E FA IL CHECKOUT DEL CODICE ---
                    releaseTempDir = Files.createTempDirectory("release-" + currentRelease.getName());
                    System.out.println("Checkout dei sorgenti in: " + releaseTempDir);
                    checkoutRelease(gitService, currentRelease.getCommit(), releaseTempDir);

                    // --- 2. ESEGUI PMD SULLA DIRECTORY ---
                    System.out.println("Avvio analisi PMD per la release...");
                    smellsMap = pmdAnalyzer.countSmellsPerMethod(releaseTempDir);
                    System.out.println("Analisi PMD completata. Trovati smells in " + smellsMap.size() + " metodi.");

                } catch (IOException e) {
                    System.err.println("Errore durante il checkout o l'analisi PMD per la release " + currentRelease.getName() + ": " + e.getMessage());
                }

                Map<String, List<MethodData>> releaseContent = getMethodsInRelease(gitService, currentRelease.getCommit());
                long totalMethodsInRelease = releaseContent.values().stream().mapToLong(List::size).sum();
                System.out.println("Trovati " + totalMethodsInRelease + " metodi in " + releaseContent.size() + " file.");

                int methodCount = 0;
                for (List<MethodData> methodsInFile : releaseContent.values()) {
                    for (MethodData methodData : methodsInFile) {
                        methodCount++;
                        if (methodCount % 500 == 0) {
                            System.out.printf("  ...analizzato metodo %d / %d%n", methodCount, totalMethodsInRelease);
                        }

                        MethodHistory methodHistory = methodsHistories.get(methodData.getUniqueID());
                        if (methodHistory == null) methodHistory = new MethodHistory(methodData.getUniqueID());

                        // NUOVO: Recupera la storia del file
                        String filePath = methodData.getUniqueID().substring(0, methodData.getUniqueID().lastIndexOf('/'));
                        FileHistory fileHistory = fileHistories.get(filePath);

                        int nSmells = smellsMap.getOrDefault(methodData.getUniqueID(), 0);


                        MethodMetrics metrics = metricsLogic.calculateMetricsForRelease(
                                methodData, methodHistory, fileHistory,
                                currentRelease, nSmells, releases, releases.size()
                        );

                        String bugginess = finalBugginessLogic.isBuggy(methodData.getUniqueID(), currentRelease, tickets) ? "yes" : "no";

                        csvWriter.writeDataRow(config.getProjectName(), methodData.getUniqueID(), currentRelease.getName(), metrics, bugginess);


                    }
                }
                // --- 4. PULIZIA DELLA DIRECTORY TEMPORANEA ---
                if (releaseTempDir != null) {
                    try {
                        System.out.println("Pulizia directory temporanea: " + releaseTempDir);
                        Files.walk(releaseTempDir)
                                .sorted(Comparator.reverseOrder())
                                .map(Path::toFile)
                                .forEach(File::delete);
                    } catch (IOException e) {
                        System.err.println("Impossibile eliminare la directory temporanea: " + releaseTempDir);
                    }
                }
            }
            // --- STAMPA DEL RIEPILOGO SUI RENAME ---
            System.out.println("\n--- ANALISI IMPATTO FILTRO RENAME ---");
            int renames = renameCount.get();
            int totalDiffs = totalDiffCount.get();
            double percentage = (totalDiffs == 0) ? 0 : ((double) renames / totalDiffs) * 100;
            System.out.printf("File modificati totali analizzati (diffs): %d%n", totalDiffs);
            System.out.printf("Operazioni di RENAME identificate e ignorate: %d%n", renames);
            System.out.printf("Percentuale di modifiche ignorate a causa di RENAME: %.2f%%%n", percentage);
            System.out.println("-----------------------------------------\n");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (gitService != null) gitService.close();
            if (csvWriter != null) try {
                csvWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("\nEsecuzione terminata per " + config.getProjectName() + ". Tempo totale: " + (System.currentTimeMillis() - totalStartTime) + "ms");
            System.out.println("Dataset salvato in: " + new File(config.getOutputCsvPath()).getAbsolutePath());
        }
    }

    // --- METODO HELPER PER IL CHECKOUT ---
    private void checkoutRelease(GitService gitService, RevCommit releaseCommit, Path targetDir) throws IOException {
        try (TreeWalk treeWalk = new TreeWalk(gitService.repository)) {
            treeWalk.addTree(releaseCommit.getTree());
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                String pathString = treeWalk.getPathString();
                if (pathString.endsWith(".java")) {
                    // Leggiamo il contenuto per il filtro successivo
                    String fileContent = gitService.getFileContentAtCommit(releaseCommit, pathString);

                    // --- USARE IL FILTRO UNIFICATO QUI ---
                    // Se il file è escluso, non lo scriviamo nemmeno su disco.
                    if (isFileExcluded(pathString, fileContent)) {
                        continue;
                    }

                    // Se il file è valido, lo scriviamo
                    Path filePath = targetDir.resolve(pathString);
                    Files.createDirectories(filePath.getParent());
                    Files.writeString(filePath, fileContent, StandardOpenOption.CREATE_NEW);
                }
            }
        }
    }

    // --- NUOVO METODO HELPER PER I FILTRI ---
    public static boolean isFileExcluded(String pathString, String fileContent) {

        String lowerCasePath = pathString.toLowerCase();

        // Regola 1: Escludi codice di test basandosi su percorsi e nomi
        if (lowerCasePath.contains("/test/")         // Qualsiasi file in una cartella 'test'
                || lowerCasePath.startsWith("test")  // File che iniziano con 'Test' (es. TestMyClass.java)
                || lowerCasePath.endsWith("test.java")) { // File che finiscono con 'Test.java'
            return true;
        }

        //Regola 2: Escludi template archetipi
        if (lowerCasePath.contains("/archetype-resources/") || lowerCasePath.contains("/archetypes/")) {
            return true;
        }



        // Regola 3: Escludi file generati
        if (fileContent.contains("Generated by") || fileContent.contains("@Generated")
                || lowerCasePath.contains("/avro/ipc/") || lowerCasePath.contains("/avro/thrift/")
                || lowerCasePath.contains("/bookkeeper/proto/") || lowerCasePath.contains("/hedwig/protocol/")) {
            return true;
        }

        // Regola 4: Escludi tool, benchmark, e utility amministrative
        if (lowerCasePath.contains("/tools/") || lowerCasePath.contains("/benchmark/")
                || lowerCasePath.endsWith("admin.java")
                || lowerCasePath.endsWith("shell.java")
                || lowerCasePath.endsWith("console.java")
                || lowerCasePath.endsWith("tool.java")) {
            return true;
        }

        return false;
    }

    private List<Release> getReleases(GitService gitService) throws IOException, GitAPIException {

        List<Release> releases = new ArrayList<>();
        List<Ref> tags = gitService.getAllTagsSortedByDate();
        int index = 0;
        try (RevWalk revWalk = new RevWalk(gitService.repository)) {
            for (Ref tag : tags) {
                String tagName = tag.getName().replace("refs/tags/", "");
                RevCommit commit = revWalk.parseCommit(tag.getObjectId());
                Release release = new Release(tagName, commit, index++);
                releases.add(release);
            }
        }
        System.out.println("----------------------------------------\n"); // Aggiungi questa linea
        System.out.println("Trovate e ordinate " + releases.size() + " release.");
        return releases;
    }

    private List<Release> filterReleases(List<Release> allReleases) {
        int countToConsider = (int) Math.ceil(allReleases.size() * 0.34);
        System.out.println("Considereremo le prime " + countToConsider + " release.");
        return allReleases.subList(0, Math.min(countToConsider, allReleases.size()));
    }

    private Map<String, List<MethodData>> getMethodsInRelease(GitService gitService, RevCommit releaseCommit) throws IOException {
        Map<String, List<MethodData>> methodsInRelease = new HashMap<>();
        try (TreeWalk treeWalk = new TreeWalk(gitService.repository)) {
            treeWalk.addTree(releaseCommit.getTree());
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                String pathString = treeWalk.getPathString();
                String fileContent = gitService.getFileContentAtCommit(releaseCommit, pathString);

                // --- USO DEL NUOVO FILTRO UNIFICATO ---
                if (isFileExcluded(pathString, fileContent)) {
                    continue;
                }

                List<MethodData> methodsInFile = new ArrayList<>();
                try {
                    StaticJavaParser.parse(fileContent).findAll(MethodDeclaration.class).forEach(md -> {
                        String signature = md.getSignature().asString();
                        String uniqueID = pathString.replace("\\", "/") + "/" + signature;
                        methodsInFile.add(new MethodData(uniqueID, signature, releaseCommit, md));
                    });
                    methodsInRelease.put(pathString.replace("\\", "/"), methodsInFile);
                } catch (Exception | StackOverflowError e) { /* Ignora errori di parsing */ }
            }
        }
        return methodsInRelease;
    }
}