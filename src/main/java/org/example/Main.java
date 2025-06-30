package org.example;

import org.example.logic.BugginessLogic;
import org.example.logic.MetricsLogic;
import org.example.model.DataRow;
import org.example.model.FileMetrics;
import org.example.model.JiraTicket;
import org.example.model.Release;
import org.example.services.CsvWriterService;
import org.example.services.GitService;
import org.example.services.JiraService;
import org.example.services.FeatureExtractor;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    private static final String PROJECT_NAME = "BOOKKEEPER";
    private static final String REPO_PATH_STR = "C:/Users/aroma/IdeaProjects/bookkeeper";
    private static final String OUTPUT_CSV_PATH = "./bookkeeper_dataset_milestone1.csv";

    public static void main(String[] args) {
        long totalStartTime = System.currentTimeMillis();
        System.out.println("Avvio Processo di Estrazione (Milestone 1) per Progetto: " + PROJECT_NAME);

        try (GitService gitService = new GitService(Paths.get(REPO_PATH_STR));
             JiraService jiraService = new JiraService()) {

            List<Release> allReleases = gitService.getReleases();
            List<JiraTicket> allTickets = jiraService.getTickets(PROJECT_NAME);
            List<RevCommit> bugCommits = gitService.getCommitsFromTickets(getTicketKeys(allTickets), PROJECT_NAME);

            BugginessLogic bugginessLogic = new BugginessLogic(allTickets, allReleases, gitService);
            MetricsLogic metricsLogic = new MetricsLogic(gitService.getGit());

            JavaParserUtil javaParserUtil = new JavaParserUtil();
            FeatureExtractor featureExtractor = new FeatureExtractor();

            List<Release> consideredReleases = filterReleases(allReleases);
            System.out.printf("Analizzeremo %d release (le prime 34%%) su %d totali.%n",
                    consideredReleases.size(), allReleases.size());

            try (CsvWriterService csvWriter = new CsvWriterService(OUTPUT_CSV_PATH)) {
                for (Release release : consideredReleases) {
                    System.out.printf("%n--- Processando Release: %s ---%n", release.getName());

                    List<String> javaFiles = gitService.findJavaFilesInRelease(release);
                    System.out.println("Analisi di " + javaFiles.size() + " file Java...");

                    int fileCounter = 0;
                    for (String filePath : javaFiles) {
                        fileCounter++;
                        if (fileCounter % 100 == 0) {
                            System.out.printf("  ...file %d/%d%n", fileCounter, javaFiles.size());
                        }

                        // 1. CALCOLA LE METRICHE A LIVELLO DI FILE (UNA VOLTA SOLA)
                        FileMetrics fileMetrics = metricsLogic.calculateMetricsForFile(filePath, release);

                        String content = gitService.getFileContent(release.getCommit(), filePath);
                        var parsedMethods = javaParserUtil.extractMethodsWithDeclarations(content);

                        for (var parsedMethod : parsedMethods) {
                            String methodName = filePath.replace("\\", "/") + "/" + parsedMethod.methodInfo.getSignature();
                            DataRow row = new DataRow(release.getName(), methodName);

                            // 2. IMPOSTA LA BUGGINESS
                            boolean isBuggy = bugginessLogic.isBuggy(filePath, release);
                            row.setBugginess(isBuggy ? "YES" : "NO");

                            // 3. IMPOSTA LE METRICHE DI FILE
                            row.setNumRevisions(fileMetrics.getNumRevisions());
                            row.setNumAuthors(fileMetrics.getNumAuthors());
                            row.setLocAdded(fileMetrics.getLocAdded());
                            row.setAvgLocAdded(fileMetrics.getAvgLocAdded());
                            row.setMaxLocAdded(fileMetrics.getMaxLocAdded());
                            row.setChurn(fileMetrics.getChurn());
                            row.setAvgChurn(fileMetrics.getAvgChurn());
                            row.setMaxChurn(fileMetrics.getMaxChurn());

                            // 4. CALCOLA E IMPOSTA LE METRICHE DI METODO
                            row.setLoc(featureExtractor.calculateLOC(parsedMethod.methodInfo));
                            // Aggiungi qui altre metriche a livello di metodo
                            // row.setCyclomaticComplexity(featureExtractor.calculateCyclomaticComplexity(parsedMethod.methodDeclaration));

                            csvWriter.writeDataRow(row, PROJECT_NAME);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("\nProcesso completato. Tempo totale: " + (System.currentTimeMillis() - totalStartTime) + "ms");
    }

    private static Set<String> getTicketKeys(List<JiraTicket> tickets) {
        Set<String> keys = new HashSet<>();
        for (JiraTicket t : tickets) keys.add(t.getKey());
        return keys;
    }

    private static List<Release> filterReleases(List<Release> allReleases) {
        if (allReleases.isEmpty()) {
            return Collections.emptyList();
        }
        int countToConsider = (int) Math.ceil(allReleases.size() * 0.34);
        if (countToConsider == 0 && !allReleases.isEmpty()) {
            countToConsider = 1;
        }
        return allReleases.subList(0, countToConsider);
    }
}