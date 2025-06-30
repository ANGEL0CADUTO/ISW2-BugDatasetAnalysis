package org.example;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.example.logic.BugginessLogic;
import org.example.logic.MetricsLogic;
import org.example.model.DataRow;
import org.example.model.JiraTicket;
import org.example.model.Release;
import org.example.services.CsvWriterService;
import org.example.services.GitService;
import org.example.services.JiraService;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.example.model.MethodInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final String PROJECT_NAME = "BOOKKEEPER";
    private static final String REPO_PATH_STR = "C:/Users/aroma/IdeaProjects/bookkeeper";

    public static void main(String[] args) {
        long totalStartTime = System.currentTimeMillis();
        System.out.println("Avvio Processo di Estrazione per il Progetto: " + PROJECT_NAME);

        try (GitService gitService = new GitService(Paths.get(REPO_PATH_STR));
             JiraService jiraService = new JiraService()) {

            // --- 1. RACCOLTA DATI GLOBALE ---
            List<Release> allReleases = gitService.getReleases();
            List<JiraTicket> allTickets = jiraService.getTickets(PROJECT_NAME);
            List<RevCommit> bugCommits = gitService.getCommitsFromTickets(getTicketKeys(allTickets), PROJECT_NAME);

            // --- 2. LOGICA DI BUSINESS ---
            BugginessLogic bugginessLogic = new BugginessLogic(allTickets, allReleases, gitService);
            MetricsLogic metricsLogic = new MetricsLogic(gitService.getGit());

            // --- 3. PREPARAZIONE PER WALK-FORWARD ---
            List<Release> releasesForAnalysis = filterReleasesForSnoring(allReleases);

            for (int i = 2; i < releasesForAnalysis.size(); i++) {
                List<Release> trainingReleases = releasesForAnalysis.subList(0, i);
                Release testingRelease = releasesForAnalysis.get(i);

                String outputCsvPath = String.format("./%s_run_%d.csv", PROJECT_NAME.toLowerCase(), i - 1);
                System.out.printf("%n--- RUN %d: Training su %d releases, Testing su %s ---%n",
                        i - 1, trainingReleases.size(), testingRelease.getName());

                try (CsvWriterService csvWriter = new CsvWriterService(outputCsvPath)) {
                    processRelease(testingRelease, trainingReleases, bugginessLogic, metricsLogic, csvWriter, gitService, bugCommits);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("\nProcesso completato. Tempo totale: " + (System.currentTimeMillis() - totalStartTime) + "ms");
    }

    private static void processRelease(Release testingRelease, List<Release> trainingReleases,
                                       BugginessLogic bugginessLogic, MetricsLogic metricsLogic,
                                       CsvWriterService csvWriter, GitService gitService, List<RevCommit> bugCommits) throws Exception {

        JavaParserUtil javaParserUtil = new JavaParserUtil();
        List<String> javaFiles = gitService.findJavaFilesInRelease(testingRelease);

        System.out.println("Analisi di " + javaFiles.size() + " file Java...");
        int fileCounter = 0;
        for (String filePath : javaFiles) {
            fileCounter++;
            if (fileCounter % 50 == 0) {
                System.out.printf("  ...file %d/%d%n", fileCounter, javaFiles.size());
            }

            String content = gitService.getFileContent(testingRelease.getCommit(), filePath);
            List<JavaParserUtil.MethodParseResult> parsedMethods = javaParserUtil.extractMethodsWithDeclarations(content);

            for (var parsedMethod : parsedMethods) {
                String methodName = filePath.replace("\\", "/") + "/" + parsedMethod.methodInfo.getSignature();
                DataRow row = new DataRow(testingRelease.getName(), methodName);

                boolean isBuggy = bugginessLogic.isBuggy(filePath, testingRelease);
                row.setBugginess(isBuggy ? "YES" : "NO");

                metricsLogic.calculateMetricsForFile(row, filePath, testingRelease, bugCommits);

                csvWriter.writeDataRow(row, PROJECT_NAME);
            }
        }
    }

    private static Set<String> getTicketKeys(List<JiraTicket> tickets) {
        Set<String> keys = new HashSet<>();
        for (JiraTicket t : tickets) keys.add(t.getKey());
        return keys;
    }

    private static List<Release> filterReleasesForSnoring(List<Release> allReleases) {
        int halfSize = (int) Math.ceil(allReleases.size() / 2.0);
        return allReleases.subList(0, halfSize);
    }
}