// in src/main/java/org/example/Main.java
package org.example;

// --- IMPORT AGGIUNTI ---
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
// --------------------

import org.example.logic.BugginessLogic;
import org.example.logic.HistoryAnalyzer;
import org.example.logic.MetricsLogic;
import org.example.model.JiraTicket;
import org.example.model.MethodData;
import org.example.model.MethodHistory;
import org.example.model.MethodMetrics;
import org.example.model.Release;
import org.example.services.CsvWriterService;
import org.example.services.GitService;
import org.example.services.JiraService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    private static final String PROJECT_NAME = "BOOKKEEPER";
    private static final String REPO_PATH_STR = "C:/Users/aroma/IdeaProjects/bookkeeper";
    private static final String OUTPUT_CSV_PATH = "./bookkeeper_milestone1.csv";

    public static void main(String[] args) {
        Main main = new Main();
        main.run();
    }

    public void run() {
        long totalStartTime = System.currentTimeMillis();
        System.out.println("Avvio generazione dataset per il progetto: " + PROJECT_NAME);

        GitService gitService = null;
        JiraService jiraService = new JiraService();
        CsvWriterService csvWriter = null;

        try {
            Path repoPath = Paths.get(REPO_PATH_STR);
            gitService = new GitService(repoPath);
            csvWriter = new CsvWriterService(OUTPUT_CSV_PATH);

            csvWriter.writeHeader("ProjectName", "MethodID", "ReleaseID", "Bugginess",
                    "LOC", "CC", "ParamCount", "NestingDepth", "NSmells",
                    "NR", "NAuth", "Churn", "MaxChurn", "AvgChurn");

            List<Release> releases = getReleases(gitService);
            List<JiraTicket> tickets = jiraService.getFixedBugTickets(PROJECT_NAME);

            BugginessLogic bugginessLogic = new BugginessLogic(releases, null);
            bugginessLogic.calculateBugLifecycles(tickets);

            // Estrai le chiavi dei ticket per passarle al GitService
            Set<String> ticketKeys = tickets.stream()
                    .map(JiraTicket::getKey)
                    .collect(Collectors.toSet());

            // Chiama il metodo corretto esistente in GitService
            Map<String, RevCommit> bugCommits = gitService.linkBugsToCommits(ticketKeys);

            HistoryAnalyzer historyAnalyzer = new HistoryAnalyzer(gitService);

            Map<String, MethodHistory> methodsHistories = historyAnalyzer.buildMethodsHistories(bugCommits);

            MetricsLogic metricsLogic = new MetricsLogic();
            BugginessLogic finalBugginessLogic = new BugginessLogic(releases, methodsHistories);
            List<Release> consideredReleases = filterReleases(releases);

            System.out.println("Inizio analisi per release e generazione CSV...");

            for (int i = 0; i < consideredReleases.size(); i++) {
                Release currentRelease = consideredReleases.get(i);
                System.out.printf("\n--- Processando release %d/%d: %s ---\n", (i + 1), consideredReleases.size(), currentRelease.getName());

                // Otteniamo una mappa [FilePath -> Lista di Metodi] per l'intera release
                Map<String, List<MethodData>> releaseMethods = getMethodsInRelease(gitService, currentRelease.getCommit());
                System.out.println("Trovati " + releaseMethods.values().stream().mapToInt(List::size).sum() + " metodi in " + releaseMethods.size() + " file.");

                int methodCount = 0;
                // Ora cicliamo sui metodi
                for (List<MethodData> methodsInFile : releaseMethods.values()) {
                    for (MethodData methodData : methodsInFile) {
                        methodCount++;
                        if (methodCount % 500 == 0) {
                            System.out.printf("  ...analizzato metodo %d%n", methodCount);
                        }

                        MethodHistory history = methodsHistories.get(methodData.getUniqueID());
                        if (history == null || history.getMethodHistories() == 0) continue;

                        MethodMetrics metrics = metricsLogic.calculateMetrics(methodData, history);
                        boolean isBuggy = finalBugginessLogic.isBuggy(methodData.getUniqueID(), currentRelease, tickets);
                        String bugginess = isBuggy ? "yes" : "no";
                        csvWriter.writeDataRow(PROJECT_NAME, methodData.getUniqueID(), currentRelease.getName(), bugginess, metrics);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (gitService != null) gitService.close();
            if (csvWriter != null) try {
                csvWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("\nEsecuzione terminata. Tempo totale: " + (System.currentTimeMillis() - totalStartTime) + "ms");
            System.out.println("Dataset salvato in: " + new File(OUTPUT_CSV_PATH).getAbsolutePath());
        }
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
                if (pathString.endsWith(".java")) {
                    String fileContent = gitService.getFileContentAtCommit(releaseCommit, pathString);
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
        }
        return methodsInRelease;
    }
}