package org.example;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.example.logic.BugginessLogic;
import org.example.model.JiraTicket;
import org.example.model.MethodData;
import org.example.model.Release;
import org.example.services.CsvWriterService;
import org.example.services.FeatureExtractor;
import org.example.services.GitService;
import org.example.services.JiraService;
import org.example.services.GitService.CommitDiffInfo;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    private static final String PROJECT_NAME = "BOOKKEEPER";
    private static final String REPO_PATH_STR = "C:/Users/aroma/IdeaProjects/bookkeeper";
    private static final String OUTPUT_CSV_PATH = "./bookkeeper_methods_dataset.csv";

    public static void main(String[] args) {
        long totalStartTime = System.currentTimeMillis();
        System.out.println("Avvio Processo di Estrazione a Livello di Metodo per Progetto: " + PROJECT_NAME);

        try (GitService gitService = new GitService(Paths.get(REPO_PATH_STR));
             JiraService jiraService = new JiraService()) {

            List<Release> allReleases = gitService.getReleases();
            List<JiraTicket> allTickets = jiraService.getTickets(PROJECT_NAME);
            List<RevCommit> bugCommits = gitService.getCommitsFromTickets(getTicketKeys(allTickets), PROJECT_NAME);

            BugginessLogic bugginessLogic = new BugginessLogic(allTickets, allReleases, gitService);
            FeatureExtractor featureExtractor = new FeatureExtractor();

            List<Release> consideredReleases = filterReleases(allReleases);
            System.out.printf("Analizzeremo %d release (le prime 34%%) su %d totali.%n",
                    consideredReleases.size(), allReleases.size());

            try (CsvWriterService csvWriter = new CsvWriterService(OUTPUT_CSV_PATH)) {
                for (Release release : consideredReleases) {
                    System.out.printf("%n--- Processando Release: %s ---%n", release.getName());
                    long releaseStartTime = System.currentTimeMillis();

                    List<String> javaFiles = gitService.findJavaFilesInRelease(release);
                    System.out.println("Analisi di " + javaFiles.size() + " file Java...");

                    int fileCounter = 0;
                    for (String filePath : javaFiles) {
                        fileCounter++;
                        if (fileCounter % 100 == 0) {
                            System.out.printf("  ...file %d/%d%n", fileCounter, javaFiles.size());
                        }

                        List<RevCommit> fileHistory = gitService.getCommitsTouchingFile(filePath, release.getCommit());
                        String content = gitService.getFileContent(release.getCommit(), filePath);
                        var cu = StaticJavaParser.parse(content);
                        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);

                        for (MethodDeclaration methodNode : methods) {
                            String signature = methodNode.getSignature().asString();
                            String methodIdentifier = filePath.replace("\\", "/") + "/" + signature;
                            MethodData row = new MethodData(release.getName(), methodIdentifier);

                            calculateMethodProcessMetrics(row, methodNode, fileHistory, gitService, filePath);

                            row.setLoc(featureExtractor.calculateLOC(methodNode));
                            row.setCyclomaticComplexity(featureExtractor.calculateCyclomaticComplexity(methodNode));

                            row.setBugginess(bugginessLogic.isBuggy(filePath, release) ? "YES" : "NO");

                            csvWriter.writeDataRow(row, PROJECT_NAME);
                        }
                    }
                    System.out.printf("--- Fine Processamento Release: %s. Tempo: %dms ---%n", release.getName(), (System.currentTimeMillis() - releaseStartTime));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("\nProcesso completato. Tempo totale: " + (System.currentTimeMillis() - totalStartTime) + "ms");
    }

    private static void calculateMethodProcessMetrics(MethodData row, MethodDeclaration methodNode,
                                                      List<RevCommit> fileHistory, GitService gitService, String filePath) throws IOException {

        int methodStartLine = methodNode.getBegin().map(p -> p.line).orElse(0);
        int methodEndLine = methodNode.getEnd().map(p -> p.line).orElse(0);

        if (methodStartLine == 0) return;

        Set<String> authors = new HashSet<>();
        int stmtAdded = 0;
        int maxStmtAdded = 0;
        int churn = 0;
        int methodHistories = 0;

        for (RevCommit commit : fileHistory) {
            CommitDiffInfo diffInfo = gitService.getCommitDiff(commit, filePath);
            boolean methodWasTouched = false;

            for (Edit edit : diffInfo.edits()) {
                int editStartInNewFile = edit.getBeginB();
                int editEndInNewFile = edit.getEndB();

                if (methodStartLine <= editEndInNewFile && methodEndLine >= editStartInNewFile) {
                    methodWasTouched = true;
                    int addedInEdit = edit.getLengthB();
                    stmtAdded += addedInEdit;
                    if (addedInEdit > maxStmtAdded) maxStmtAdded = addedInEdit;
                    churn += edit.getLengthA() + edit.getLengthB();
                }
            }

            if (methodWasTouched) {
                methodHistories++;
                authors.add(commit.getAuthorIdent().getEmailAddress());
            }
        }

        row.setMethodHistories(methodHistories);
        row.setNumAuthors(authors.size());
        row.setStmtAdded(stmtAdded);
        row.setMaxStmtAdded(maxStmtAdded);
        row.setAvgStmtAdded(methodHistories > 0 ? stmtAdded / methodHistories : 0);
        row.setChurn(churn);
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