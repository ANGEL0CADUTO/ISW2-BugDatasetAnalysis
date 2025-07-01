// in src/main/java/org/example/logic/HistoryAnalyzer.java
package org.example.logic;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.Statement;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.example.model.MethodData;
import org.example.model.MethodHistory;
import org.example.services.GitService;
import org.example.services.JavaParserUtil;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class HistoryAnalyzer {

    private final GitService gitService;
    private final JavaParserUtil javaParserUtil;

    public HistoryAnalyzer(GitService gitService) {
        this.gitService = gitService;
        this.javaParserUtil = new JavaParserUtil();
    }

    public Map<String, MethodHistory> buildMethodsHistories(Map<String, RevCommit> bugCommits)
            throws GitAPIException, IOException {

        System.out.println("Inizio costruzione della storia dei metodi. Questo è il passo più lungo...");

        Map<String, MethodHistory> histories = new HashMap<>();
        Iterable<RevCommit> allCommits = gitService.getAllCommits();

        int commitCount = 0;
        for (RevCommit commit : allCommits) {
            commitCount++;
            if (commitCount % 1000 == 0) {
                System.out.printf("Analisi commit %d...%n", commitCount);
            }
            if (commit.getParentCount() == 0) continue; // Salta il primo commit

            List<DiffEntry> diffs = gitService.getChangedFilesInCommit(commit);
            for (DiffEntry diff : diffs) {
                String newPath = diff.getNewPath();
                if (!newPath.endsWith(".java")) continue;

                Map<String, MethodData> methodsBefore = getMethodsFromFile(commit.getParent(0), diff.getOldPath());
                Map<String, MethodData> methodsAfter = getMethodsFromFile(commit, newPath);

                compareMethodVersions(methodsBefore, methodsAfter, commit, histories);
            }

            // Associa il bug fix ai metodi che esistevano in questo commit
            if (bugCommits.containsKey(commit.getName())) {
                for (DiffEntry diff : diffs) {
                    if(diff.getNewPath().endsWith(".java")) {
                        Map<String, MethodData> methodsInCommit = getMethodsFromFile(commit, diff.getNewPath());
                        for(String methodId : methodsInCommit.keySet()) {
                            histories.computeIfAbsent(methodId, MethodHistory::new).addBugFixCommit(commit);
                        }
                    }
                }
            }
        }
        System.out.println("Analisi storica completata. Trovate le storie di " + histories.size() + " metodi unici.");
        return histories;
    }

    private Map<String, MethodData> getMethodsFromFile(RevCommit commit, String filePath) throws IOException {
        Map<String, MethodData> methods = new HashMap<>();
        if (commit == null || filePath.equals("/dev/null")) return methods;

        String fileContent = gitService.getFileContent(commit, filePath);
        if(fileContent.isEmpty()) return methods;

        List<JavaParserUtil.MethodParseResult> parsedMethods = javaParserUtil.extractMethodsWithDeclarations(fileContent);
        for (JavaParserUtil.MethodParseResult parsed : parsedMethods) {
            String uniqueID = filePath.replace("\\", "/") + "/" + parsed.methodInfo.getSignature();
            methods.put(uniqueID, new MethodData(uniqueID, commit, parsed.methodDeclaration, fileContent));
        }
        return methods;
    }

    private void compareMethodVersions(Map<String, MethodData> before, Map<String, MethodData> after, RevCommit currentCommit, Map<String, MethodHistory> histories) {
        // Metodi modificati o cancellati
        for (MethodData methodBefore : before.values()) {
            MethodData methodAfter = after.get(methodBefore.getUniqueID());

            if (methodAfter != null) { // Il metodo esiste ancora
                List<String> stmtsBefore = getStatementsAsString(methodBefore.getDeclaration());
                List<String> stmtsAfter = getStatementsAsString(methodAfter.getDeclaration());

                if (!stmtsBefore.equals(stmtsAfter)) { // Il metodo è stato modificato
                    Patch<String> patch = DiffUtils.diff(stmtsBefore, stmtsAfter);
                    int added = 0;
                    int deleted = 0;
                    for (AbstractDelta<String> delta : patch.getDeltas()) {
                        switch (delta.getType()) {
                            case INSERT: added += delta.getTarget().getLines().size(); break;
                            case DELETE: deleted += delta.getSource().getLines().size(); break;
                            case CHANGE:
                                added += delta.getTarget().getLines().size();
                                deleted += delta.getSource().getLines().size();
                                break;
                            default: break;
                        }
                    }
                    histories.computeIfAbsent(methodBefore.getUniqueID(), MethodHistory::new).addVersion(methodAfter, added, deleted);
                }
            }
        }
        // Metodi aggiunti
        for (MethodData methodAfter : after.values()) {
            if (!before.containsKey(methodAfter.getUniqueID())) {
                int stmtsAdded = countStatements(methodAfter.getDeclaration());
                histories.computeIfAbsent(methodAfter.getUniqueID(), MethodHistory::new).addVersion(methodAfter, stmtsAdded, 0);
            }
        }
    }

    private List<String> getStatementsAsString(MethodDeclaration md) {
        if (!md.getBody().isPresent()) return Collections.emptyList();
        return md.getBody().get().getStatements().stream()
                .map(Statement::toString)
                .collect(Collectors.toList());
    }

    private int countStatements(MethodDeclaration md) {
        return md.getBody().map(body -> body.getStatements().size()).orElse(0);
    }
}