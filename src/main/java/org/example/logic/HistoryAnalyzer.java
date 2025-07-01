package org.example.logic;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.example.model.MethodHistory;
import org.example.model.MethodMetrics;
import org.example.model.Release;
import org.example.services.FeatureExtractor;
import org.example.services.GitService;
import org.example.services.GitService.CommitDiffInfo;

import java.util.*;

public class HistoryAnalyzer {

    private final GitService gitService;
    private final List<Release> releases;
    private final Set<String> bugCommitHashes;
    private final FeatureExtractor featureExtractor;
    public final Map<String, MethodHistory> methodHistories = new HashMap<>(); // Key: "filepath/signature"

    public HistoryAnalyzer(GitService gitService, List<Release> releases, List<RevCommit> bugCommits) throws Exception {
        this.gitService = gitService;
        this.releases = releases;
        this.bugCommitHashes = new HashSet<>();
        for (RevCommit bc : bugCommits) {
            this.bugCommitHashes.add(bc.getName());
        }
        this.featureExtractor = new FeatureExtractor();

        processRepositoryHistory();
    }

    private void processRepositoryHistory() throws Exception {
        System.out.println("Avvio analisi single-pass della storia del repository...");
        List<RevCommit> allCommits = new ArrayList<>();
        gitService.getGit().log().all().call().forEach(allCommits::add);
        Collections.reverse(allCommits); // Ordina dal più vecchio al più nuovo

        int count = 0;
        for (RevCommit commit : allCommits) {
            count++;
            if (count % 200 == 0) {
                System.out.printf("  ...analizzato commit %d/%d%n", count, allCommits.size());
            }
            if (commit.getParentCount() == 0) continue;

            List<String> touchedFiles = gitService.getTouchedFiles(commit);
            for (String filePath : touchedFiles) {
                if (!filePath.endsWith(".java")) continue;

                CommitDiffInfo diffInfo = gitService.getCommitDiff(commit, filePath);
                String oldContent = "";
                try {
                    oldContent = gitService.getFileContent(commit.getParent(0), filePath);
                } catch (Exception e) { /* File non esisteva prima (ADD) */ }

                String newContent = gitService.getFileContent(commit, filePath);

                List<MethodDeclaration> oldMethods = StaticJavaParser.parse(oldContent).findAll(MethodDeclaration.class);
                List<MethodDeclaration> newMethods = StaticJavaParser.parse(newContent).findAll(MethodDeclaration.class);

                updateMetricsForFile(filePath, commit, diffInfo, oldMethods, newMethods);
            }
        }
        System.out.println("Analisi della storia completata.");
    }

    private void updateMetricsForFile(String filePath, RevCommit commit, CommitDiffInfo diffInfo, List<MethodDeclaration> oldMethods, List<MethodDeclaration> newMethods) {
        for (MethodDeclaration newMethod : newMethods) {
            String signature = newMethod.getSignature().asString();
            String methodKey = filePath.replace("\\", "/") + "/" + signature;

            MethodHistory history = methodHistories.computeIfAbsent(methodKey, k -> new MethodHistory(filePath, signature));

            // Trova la release corrente
            Release currentRelease = findReleaseForCommit(commit);
            if (currentRelease == null) continue; // Commit non appartiene a nessuna release

            MethodMetrics metrics = history.metricsPerRelease.computeIfAbsent(currentRelease.getName(), k -> new MethodMetrics());

            // Aggiorna metriche statiche per la release corrente
            metrics.loc = featureExtractor.calculateLOC(newMethod);
            metrics.cyclomaticComplexity = featureExtractor.calculateCyclomaticComplexity(newMethod);

            // Aggiorna metriche di processo
            boolean wasModified = wasMethodModified(newMethod, oldMethods, diffInfo.edits());
            if (wasModified) {
                history.authors.add(commit.getAuthorIdent().getEmailAddress());
                int churn = diffInfo.linesAdded() + diffInfo.linesDeleted();

                metrics.numRevisions += 1;
                metrics.locAdded += diffInfo.linesAdded();
                if (diffInfo.linesAdded() > metrics.maxLocAdded) {
                    metrics.maxLocAdded = diffInfo.linesAdded();
                }
                metrics.churn += churn;
                if (churn > metrics.maxChurn) {
                    metrics.maxChurn = churn;
                }
            }
            if (bugCommitHashes.contains(commit.getName())) {
                metrics.numFixes += 1;
            }
        }
    }

    private boolean wasMethodModified(MethodDeclaration method, List<MethodDeclaration> oldMethods, List<Edit> edits) {
        // Criterio 1: il metodo non esisteva prima (nuovo metodo)
        if (oldMethods.stream().noneMatch(old -> old.getSignature().asString().equals(method.getSignature().asString()))) {
            return true;
        }

        // Criterio 2: le linee del metodo si sovrappongono a un "hunk" di modifica
        int start = method.getBegin().get().line;
        int end = method.getEnd().get().line;
        for (Edit edit : edits) {
            if (Math.max(start, edit.getBeginB()) <= Math.min(end, edit.getEndB())) {
                return true;
            }
        }
        return false;
    }

    private Release findReleaseForCommit(RevCommit commit) {
        for (int i = releases.size() - 1; i >= 0; i--) {
            if (commit.getCommitTime() <= releases.get(i).getCommit().getCommitTime()) {
                return releases.get(i);
            }
        }
        return null;
    }
}