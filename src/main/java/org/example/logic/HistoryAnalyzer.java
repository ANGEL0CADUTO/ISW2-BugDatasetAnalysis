package org.example.logic;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.Statement;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.example.model.MethodHistory;
import org.example.services.GitService;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class HistoryAnalyzer {
    private final GitService gitService;

    public HistoryAnalyzer(GitService gitService) { this.gitService = gitService; }

    public Map<String, MethodHistory> buildMethodsHistories(Map<String, RevCommit> bugCommits) throws GitAPIException, IOException {
        System.out.println("Inizio costruzione della storia dei metodi...");
        Map<String, MethodHistory> histories = new HashMap<>();
        Iterable<RevCommit> allCommits = gitService.getAllCommits();

        int commitCount = 0;
        for (RevCommit commit : allCommits) {
            commitCount++;
            if (commitCount % 500 == 0) System.out.printf("Analisi commit %d...%n", commitCount);
            if (commit.getParentCount() == 0) continue;

            List<DiffEntry> diffs = gitService.getChangedFilesInCommit(commit);
            for (DiffEntry diff : diffs) {
                if (!diff.getNewPath().endsWith(".java")) continue;
                String contentBefore = gitService.getFileContentAtCommit(commit.getParent(0), diff.getOldPath());
                String contentAfter = gitService.getFileContentAtCommit(commit, diff.getNewPath());
                Map<String, List<String>> stmtsBefore = getMethodStatements(contentBefore);
                Map<String, List<String>> stmtsAfter = getMethodStatements(contentAfter);
                updateHistoriesWithDiff(diff.getNewPath(), stmtsBefore, stmtsAfter, commit, histories);
            }
            if (bugCommits.containsKey(commit.getName())) {
                for (DiffEntry diff : diffs) {
                    if (diff.getNewPath().endsWith(".java")) {
                        Map<String, List<String>> methodsInCommit = getMethodStatements(gitService.getFileContentAtCommit(commit, diff.getNewPath()));
                        for (String signature : methodsInCommit.keySet()) {
                            String uniqueID = diff.getNewPath().replace("\\", "/") + "/" + signature;
                            histories.computeIfAbsent(uniqueID, MethodHistory::new).addFix(commit);
                        }
                    }
                }
            }
        }
        System.out.println("Analisi storica completata.");
        return histories;
    }

    private Map<String, List<String>> getMethodStatements(String fileContent) {
        if (fileContent.isEmpty()) return Collections.emptyMap();
        Map<String, List<String>> methods = new HashMap<>();
        try {
            StaticJavaParser.parse(fileContent).findAll(MethodDeclaration.class).forEach(md -> {
                String signature = md.getSignature().asString();
                List<String> statements = md.getBody().map(body -> body.getStatements().stream().map(Statement::toString).collect(Collectors.toList())).orElse(Collections.emptyList());
                methods.put(signature, statements);
            });
        } catch (Exception | StackOverflowError e) { /* Ignora */ }
        return methods;
    }

    private void updateHistoriesWithDiff(String filePath, Map<String, List<String>> before, Map<String, List<String>> after, RevCommit commit, Map<String, MethodHistory> histories) {
        Set<String> allSignatures = new HashSet<>(before.keySet());
        allSignatures.addAll(after.keySet());
        for (String signature : allSignatures) {
            List<String> stmtsBefore = before.getOrDefault(signature, Collections.emptyList());
            List<String> stmtsAfter = after.getOrDefault(signature, Collections.emptyList());
            if (!stmtsBefore.equals(stmtsAfter)) {
                int added = 0;
                int deleted = 0;
                Patch<String> patch = DiffUtils.diff(stmtsBefore, stmtsAfter);
                for (AbstractDelta<String> delta : patch.getDeltas()) {
                    if (delta.getType() == DeltaType.INSERT) added += delta.getTarget().getLines().size();
                    else if (delta.getType() == DeltaType.DELETE) deleted += delta.getSource().getLines().size();
                    else if (delta.getType() == DeltaType.CHANGE) {
                        added += delta.getTarget().getLines().size();
                        deleted += delta.getSource().getLines().size();
                    }
                }
                String uniqueID = filePath.replace("\\", "/") + "/" + signature;
                histories.computeIfAbsent(uniqueID, MethodHistory::new).addChange(commit, added, deleted);
            }
        }
    }
}