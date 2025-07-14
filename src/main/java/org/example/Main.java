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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Main {

    // 1. INTRODUZIONE DI UN LOGGER STANDARD
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static final AtomicInteger renameCount = new AtomicInteger(0);
    public static final AtomicInteger totalDiffCount = new AtomicInteger(0);

    public static void main(String[] args) {
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

        new Main().run(bookkeeperConfig);
        new Main().run(avroConfig);
    }

    /**
     * Metodo principale orchestratore, ora con complessità ridotta.
     * Delega i compiti principali a metodi privati.
     */
    public void run(ProjectConfig config) {
        long totalStartTime = System.currentTimeMillis();
        LOGGER.log(Level.INFO, "Avvio generazione dataset per il progetto: {0}", config.getProjectName());

        // --- GESTIONE RISORSE: approccio classico con try-finally ---
        GitService gitService = null;
        CsvWriterService csvWriter = null;
        try {
            gitService = new GitService(Paths.get(config.getRepoPath()));
            csvWriter = new CsvWriterService(config.getOutputCsvPath());

            // Fase 1: Setup e raccolta dati preliminare
            csvWriter.writeHeader("ProjectName", "MethodID", "ReleaseID",
                    "LOC", "CC", "ParamCount", "NestingDepth", "NSmells",
                    "NR", "NAuth", "Churn", "MaxChurn", "NFix", "AvgChurn",
                    "ClassNR", "ClassNAuth", "ClassChurn", "AvgClassChurn", "Bugginess");

            List<Release> allReleases = getReleases(gitService);
            List<JiraTicket> allTickets = new JiraService().getFixedBugTickets(config.getProjectName());

            // Fase 2: Analisi storica e logica
            BugginessLogic bugginessLogic = new BugginessLogic(allReleases, null);
            bugginessLogic.calculateBugLifecycles(allTickets);

            HistoryAnalyzer.AnalysisResult analysisResult = analyzeHistory(gitService, allTickets);

            // Fase 3: Processamento per release
            List<Release> consideredReleases = filterReleases(allReleases);
            processReleases(config, consideredReleases, analysisResult, allReleases, allTickets, csvWriter, gitService);

            printSummary();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Errore fatale durante l'esecuzione del progetto " + config.getProjectName(), e);
        } finally {
            // Chiusura sicura delle risorse
            if (gitService != null) {
                gitService.close(); // Assumendo che GitService abbia un metodo close()
            }
            if (csvWriter != null) {
                try {
                    csvWriter.close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Errore durante la chiusura del CsvWriter.", e);
                }
            }
        }

        LOGGER.log(Level.INFO, "Esecuzione terminata per {0}. Tempo totale: {1}ms",
                new Object[]{config.getProjectName(), (System.currentTimeMillis() - totalStartTime)});
        LOGGER.log(Level.INFO, "Dataset salvato in: {0}", new File(config.getOutputCsvPath()).getAbsolutePath());
    }

    /**
     * Esegue l'analisi storica per costruire le storie di metodi e file.
     */
    private HistoryAnalyzer.AnalysisResult analyzeHistory(GitService gitService, List<JiraTicket> tickets) throws GitAPIException, IOException {
        Set<String> ticketKeys = tickets.stream().map(JiraTicket::getKey).collect(Collectors.toSet());
        Map<String, RevCommit> bugCommits = gitService.linkBugsToCommits(ticketKeys);
        HistoryAnalyzer historyAnalyzer = new HistoryAnalyzer(gitService);
        return historyAnalyzer.analyzeCommitHistory(bugCommits);
    }

    /**
     * Itera sulle release selezionate per calcolare le metriche e scrivere il CSV.
     */
    private void processReleases(ProjectConfig config, List<Release> releasesToProcess, HistoryAnalyzer.AnalysisResult analysisResult,
                                 List<Release> allReleases, List<JiraTicket> allTickets, CsvWriterService csvWriter, GitService gitService) throws IOException {
        LOGGER.info("Inizio analisi per release e generazione CSV...");

        MetricsLogic metricsLogic = new MetricsLogic();
        BugginessLogic finalBugginessLogic = new BugginessLogic(allReleases, analysisResult.methodHistories);

        for (Release currentRelease : releasesToProcess) {
            LOGGER.log(Level.INFO, "--- Processando release {0} ---", currentRelease.getName());

            Map<String, List<MethodData>> releaseContent = getMethodsInRelease(gitService, currentRelease.getCommit());
            Map<String, Integer> smellsMap = analyzeSmellsForRelease(gitService, currentRelease);

            long totalMethods = releaseContent.values().stream().mapToLong(List::size).sum();
            LOGGER.log(Level.INFO, "Trovati {0} metodi in {1} file.", new Object[]{totalMethods, releaseContent.size()});

            int methodCount = 0;
            for (List<MethodData> methodsInFile : releaseContent.values()) {
                for (MethodData methodData : methodsInFile) {
                    methodCount++;
                    if (methodCount % 500 == 0) {
                        LOGGER.log(Level.INFO, "  ...analizzato metodo {0} / {1}", new Object[]{methodCount, totalMethods});
                    }

                    calculateAndWriteMetrics(methodData, analysisResult, smellsMap, metricsLogic,
                            finalBugginessLogic, currentRelease, allReleases, allTickets, config, csvWriter);
                }
            }
        }
    }

    /**
     * Esegue il checkout di una release in una dir temporanea ed esegue PMD.
     */
    private Map<String, Integer> analyzeSmellsForRelease(GitService gitService, Release release) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("release-" + release.getName());
            checkoutRelease(gitService, release.getCommit(), tempDir);

            LOGGER.info("Avvio analisi PMD...");
            Map<String, Integer> smells = new PmdAnalyzer().countSmellsPerMethod(tempDir);
            LOGGER.log(Level.INFO, "Analisi PMD completata. Trovati smells in {0} metodi.", smells.size());
            return smells;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore durante il checkout o l'analisi PMD per la release " + release.getName(), e);
            return Collections.emptyMap();
        } finally {
            if (tempDir != null) {
                try {
                    // Pulisce la directory temporanea
                    Files.walk(tempDir)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Impossibile eliminare la directory temporanea: " + tempDir, e);
                }
            }
        }
    }

    /**
     * Calcola tutte le metriche per un singolo metodo e scrive la riga nel CSV.
     */
    private void calculateAndWriteMetrics(MethodData methodData, HistoryAnalyzer.AnalysisResult analysisResult,
                                          Map<String, Integer> smellsMap, MetricsLogic metricsLogic, BugginessLogic bugginessLogic,
                                          Release currentRelease, List<Release> allReleases, List<JiraTicket> allTickets,
                                          ProjectConfig config, CsvWriterService csvWriter) throws IOException {

        MethodHistory methodHistory = analysisResult.methodHistories.get(methodData.getUniqueID());
        if (methodHistory == null) methodHistory = new MethodHistory(methodData.getUniqueID());

        String filePath = methodData.getUniqueID().substring(0, methodData.getUniqueID().lastIndexOf('/'));
        FileHistory fileHistory = analysisResult.fileHistories.get(filePath);

        int nSmells = smellsMap.getOrDefault(methodData.getUniqueID(), 0);

        MethodMetrics metrics = metricsLogic.calculateMetricsForRelease(
                methodData, methodHistory, fileHistory,
                currentRelease, nSmells, allReleases, allReleases.size()
        );

        String bugginess = bugginessLogic.isBuggy(methodData.getUniqueID(), currentRelease, allTickets) ? "yes" : "no";

        csvWriter.writeDataRow(config.getProjectName(), methodData.getUniqueID(), currentRelease.getName(), metrics, bugginess);
    }

    /**
     * Esegue il checkout del contenuto di un commit in una directory di destinazione.
     */
    private void checkoutRelease(GitService gitService, RevCommit releaseCommit, Path targetDir) throws IOException {
        try (TreeWalk treeWalk = new TreeWalk(gitService.repository)) {
            treeWalk.addTree(releaseCommit.getTree());
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                String pathString = treeWalk.getPathString();
                String fileContent = gitService.getFileContentAtCommit(releaseCommit, pathString);

                if (pathString.endsWith(".java") && !isFileExcluded(pathString, fileContent)) {
                    Path filePath = targetDir.resolve(pathString);
                    Files.createDirectories(filePath.getParent());
                    Files.writeString(filePath, fileContent, StandardOpenOption.CREATE_NEW);
                }
            }
        }
    }

    /**
     * Logica di filtraggio unificata per escludere file non rilevanti.
     */
    public static boolean isFileExcluded(String pathString, String fileContent) {
        String lowerCasePath = pathString.toLowerCase();
        // Controlla se il contenuto è nullo o vuoto prima di usarlo
        String content = (fileContent == null) ? "" : fileContent;

        return lowerCasePath.contains("/test/")
                || lowerCasePath.startsWith("test")
                || lowerCasePath.endsWith("test.java")
                || lowerCasePath.contains("/archetype-resources/")
                || lowerCasePath.contains("/archetypes/")
                || content.contains("Generated by")
                || content.contains("@Generated")
                || lowerCasePath.contains("/avro/ipc/")
                || lowerCasePath.contains("/avro/thrift/")
                || lowerCasePath.contains("/bookkeeper/proto/")
                || lowerCasePath.contains("/hedwig/protocol/")
                || lowerCasePath.contains("/tools/")
                || lowerCasePath.contains("/benchmark/")
                || lowerCasePath.endsWith("admin.java")
                || lowerCasePath.endsWith("shell.java")
                || lowerCasePath.endsWith("console.java")
                || lowerCasePath.endsWith("tool.java");
    }

    private List<Release> getReleases(GitService gitService) throws IOException, GitAPIException {
        List<Release> releases = new ArrayList<>();
        List<Ref> tags = gitService.getAllTagsSortedByDate();
        int index = 0;
        try (RevWalk revWalk = new RevWalk(gitService.repository)) {
            for (Ref tag : tags) {
                String tagName = tag.getName().replace("refs/tags/", "");
                RevCommit commit = revWalk.parseCommit(tag.getObjectId());
                releases.add(new Release(tagName, commit, index++));
            }
        }
        LOGGER.log(Level.INFO, "----------------------------------------%n");
        LOGGER.log(Level.INFO, "Trovate e ordinate {0} release.", releases.size());
        return releases;
    }

    private List<Release> filterReleases(List<Release> allReleases) {
        int countToConsider = (int) Math.ceil(allReleases.size() * 0.34);
        LOGGER.log(Level.INFO, "Considereremo le prime {0} release.", countToConsider);
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

                if (pathString.endsWith(".java") && !isFileExcluded(pathString, fileContent)) {
                    List<MethodData> methodsInFile = new ArrayList<>();
                    try {
                        StaticJavaParser.parse(fileContent).findAll(MethodDeclaration.class).forEach(md -> {
                            String signature = md.getSignature().asString();
                            String uniqueID = pathString.replace("\\", "/") + "/" + signature;
                            methodsInFile.add(new MethodData(uniqueID, signature, releaseCommit, md));
                        });
                        methodsInRelease.put(pathString.replace("\\", "/"), methodsInFile);
                    } catch (Exception | StackOverflowError e) {
                        LOGGER.log(Level.WARNING, "Errore di parsing, file saltato: " + pathString, e);
                    }
                }
            }
        }
        return methodsInRelease;
    }

    private void printSummary() {
        LOGGER.log(Level.INFO, "%n--- ANALISI IMPATTO FILTRO RENAME ---");
        int renames = renameCount.get();
        int totalDiffs = totalDiffCount.get();
        double percentage = (totalDiffs == 0) ? 0 : ((double) renames / totalDiffs) * 100;
        LOGGER.log(Level.INFO, "File modificati totali analizzati (diffs): {0}", totalDiffs);
        LOGGER.log(Level.INFO, "Operazioni di RENAME identificate e ignorate: {0}", renames);
        LOGGER.log(Level.INFO, "Percentuale di modifiche ignorate a causa di RENAME: {0,number,#.##}%", percentage);
        LOGGER.log(Level.INFO, "-----------------------------------------%n");
    }
}