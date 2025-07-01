package org.example;

import org.eclipse.jgit.revwalk.RevCommit;
import org.example.logic.BugginessLogic;
import org.example.logic.HistoryAnalyzer;
import org.example.model.JiraTicket;
import org.example.model.MethodData;
import org.example.model.MethodHistory;
import org.example.model.MethodMetrics;
import org.example.model.Release;
import org.example.services.CsvWriterService;
import org.example.services.GitService;
import org.example.services.JiraService;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    private static final String PROJECT_NAME = "BOOKKEEPER";
    private static final String REPO_PATH_STR = "C:/Users/aroma/IdeaProjects/bookkeeper";
    private static final String OUTPUT_CSV_PATH = "./bookkeeper_dataset_final.csv";

    public static void main(String[] args) {
        long totalStartTime = System.currentTimeMillis();
        System.out.println("Avvio Processo di Estrazione per Progetto: " + PROJECT_NAME);

        try (GitService gitService = new GitService(Paths.get(REPO_PATH_STR));
             JiraService jiraService = new JiraService()) {

            // --- 1. RACCOLTA DATI E ANALISI STORICA ---
            List<Release> allReleases = gitService.getReleases();
            List<JiraTicket> allTickets = jiraService.getTickets(PROJECT_NAME);
            List<RevCommit> bugCommits = gitService.getCommitsFromTickets(getTicketKeys(allTickets), PROJECT_NAME);

            // Il pre-calcolo pesante avviene qui
            HistoryAnalyzer historyAnalyzer = new HistoryAnalyzer(gitService, allReleases, bugCommits);

            BugginessLogic bugginessLogic = new BugginessLogic(allTickets, allReleases, gitService);

            // --- 2. GENERAZIONE DATASET ---
            List<Release> consideredReleases = filterReleases(allReleases);
            System.out.printf("Generazione dataset per %d release (le prime 34%%).%n", consideredReleases.size());

            try (CsvWriterService csvWriter = new CsvWriterService(OUTPUT_CSV_PATH)) {
                for (Release release : consideredReleases) {
                    System.out.println("Processando release: " + release.getName());

                    for (MethodHistory methodHistory : historyAnalyzer.methodHistories.values()) {
                        MethodMetrics metrics = methodHistory.metricsPerRelease.get(release.getName());

                        // Considera solo i metodi che esistono in questa release
                        if (metrics == null) continue;

                        String methodIdentifier = methodHistory.file.replace("\\", "/") + "/" + methodHistory.signature;
                        MethodData row = new MethodData(release.getName(), methodIdentifier);

                        populateRowWithMetrics(row, metrics, methodHistory.authors.size());
                        row.setBugginess(bugginessLogic.isBuggy(methodHistory.file, release) ? "YES" : "NO");

                        csvWriter.writeDataRow(row, PROJECT_NAME);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("\nProcesso completato. Tempo totale: " + (System.currentTimeMillis() - totalStartTime) + "ms");
    }

    private static void populateRowWithMetrics(MethodData row, MethodMetrics metrics, int numAuthors) {
        row.setLoc(metrics.loc);
        row.setCyclomaticComplexity(metrics.cyclomaticComplexity);
        row.setNumRevisions(metrics.numRevisions);
        row.setNumAuthors(numAuthors);
        row.setStmtAdded(metrics.locAdded);
        row.setMaxStmtAdded(metrics.maxLocAdded);
        row.setAvgStmtAdded(metrics.numRevisions > 0 ? metrics.locAdded / metrics.numRevisions : 0);
        row.setChurn(metrics.churn);
    }

    private static Set<String> getTicketKeys(List<JiraTicket> tickets) {
        Set<String> keys = new HashSet<>();
        for (JiraTicket t : tickets) keys.add(t.getKey());
        return keys;
    }

    private static List<Release> filterReleases(List<Release> allReleases) {
        if (allReleases.isEmpty()) return Collections.emptyList();
        int countToConsider = (int) Math.ceil(allReleases.size() * 0.34);
        return allReleases.subList(0, Math.min(countToConsider, allReleases.size()));
    }
}