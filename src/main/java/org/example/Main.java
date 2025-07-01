// in src/main/java/org/example/Main.java
package org.example;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.example.logic.BugginessLogic;
import org.example.logic.HistoryAnalyzer;
import org.example.logic.MetricsLogic;
import org.example.model.*; // Importa tutto dal model
import org.example.services.CsvWriterService;
import org.example.services.GitService;
import org.example.services.JiraService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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

            // --- FASE 1: PRE-CALCOLO GLOBALE ---

            // 1.1 Recupera le release da Git (serve per mappare le date ai ticket)
            List<Release> releases = getReleases(gitService);

            // 1.2 Recupera i ticket da Jira
            List<JiraTicket> tickets = jiraService.getFixedBugTickets(PROJECT_NAME);

            // 1.3 Calcola IV, OV, FV per ogni ticket usando Proportion
            BugginessLogic bugginessLogic = new BugginessLogic(releases);
            bugginessLogic.calculateBugLifecycles(tickets);

            // 1.4 Linka i ticket ai commit e costruisci la storia dei metodi
            Map<String, RevCommit> bugCommits = gitService.linkBugsToCommitsFromTickets(tickets);
            HistoryAnalyzer historyAnalyzer = new HistoryAnalyzer(gitService);
            Map<String, MethodHistory> methodsHistories = historyAnalyzer.buildMethodsHistories(bugCommits);

            // --- FASE 2: ANALISI PER RELEASE E CREAZIONE CSV ---
            MetricsLogic metricsLogic = new MetricsLogic();
            List<Release> consideredReleases = filterReleases(releases);

            System.out.println("Inizio analisi per release e generazione CSV...");

            for (int i = 0; i < consideredReleases.size(); i++) {
                Release currentRelease = consideredReleases.get(i);
                System.out.printf("\n--- Processando release %d/%d: %s ---\n", (i + 1), consideredReleases.size(), currentRelease.getName());

                Map<String, MethodData> methodsInRelease = gitService.getMethodsInRelease(currentRelease.getCommit());
                System.out.println("Trovati " + methodsInRelease.size() + " metodi nella release.");

                for (MethodData methodData : methodsInRelease.values()) {
                    MethodHistory history = methodsHistories.get(methodData.getUniqueID());
                    if (history == null || history.getMethodHistories() == 0) continue;

                    // Calcola le metriche
                    MethodMetrics metrics = metricsLogic.calculateMetrics(methodData, history);

                    // Calcola la Bugginess (con la logica Post-Release)
                    boolean isBuggy = bugginessLogic.isMethodBuggyInRelease(history, currentRelease);
                    String bugginess = isBuggy ? "yes" : "no";

                    // Scrivi la riga
                    csvWriter.writeDataRow(PROJECT_NAME, methodData.getUniqueID(), currentRelease.getName(), bugginess, metrics.toList());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (gitService != null) gitService.close();
            if (csvWriter != null) try { csvWriter.close(); } catch (IOException e) { e.printStackTrace(); }
            System.out.println("\nEsecuzione terminata. Tempo totale: " + (System.currentTimeMillis() - totalStartTime) + "ms");
            System.out.println("Dataset salvato in: " + new File(OUTPUT_CSV_PATH).getAbsolutePath());
        }
    }

    private List<Release> getReleases(GitService gitService) throws IOException {
        List<Release> releases = new ArrayList<>();
        List<Ref> tags = gitService.getAllReleasesSortedByDate();
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
        // Le specifiche dicono "ignore the last 66%", che significa prendere il primo 34%
        int countToConsider = (int) Math.ceil(allReleases.size() * 0.34);
        System.out.println("Considereremo le prime " + countToConsider + " release.");
        return allReleases.subList(0, Math.min(countToConsider, allReleases.size()));
    }
}