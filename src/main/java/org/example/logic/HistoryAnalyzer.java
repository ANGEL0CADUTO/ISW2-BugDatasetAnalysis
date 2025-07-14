// in src/main/java/org/example/logic/HistoryAnalyzer.java
package org.example.logic;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.Statement;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.example.model.FileHistory;
import org.example.model.MethodHistory;
import org.example.services.GitService;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class HistoryAnalyzer {
    private final GitService gitService;
    private final ParserConfiguration parserConfig;

    public static class AnalysisResult {
        public final Map<String, MethodHistory> methodHistories;
        public final Map<String, FileHistory> fileHistories;

        public AnalysisResult(Map<String, MethodHistory> methodHistories, Map<String, FileHistory> fileHistories) {
            this.methodHistories = methodHistories;
            this.fileHistories = fileHistories;
        }
    }

    public HistoryAnalyzer(GitService gitService) {
        this.gitService = gitService;
        this.parserConfig = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17_PREVIEW);
        StaticJavaParser.setConfiguration(this.parserConfig);
    }

    public AnalysisResult analyzeCommitHistory(Map<String, RevCommit> bugCommits) throws GitAPIException, IOException {
        System.out.println("Inizio costruzione della storia dei metodi e dei file...");
        Map<String, MethodHistory> methodHistories = new HashMap<>();
        Map<String, FileHistory> fileHistories = new HashMap<>();

        Iterable<RevCommit> allCommits = gitService.getAllCommits();
        int commitCount = 0;

        for (RevCommit commit : allCommits) {
            commitCount++;
            if (commitCount % 500 == 0) System.out.printf("Analisi commit %d...%n", commitCount);

            try {
                if (commit.getParentCount() == 0) continue;

                // Determina una sola volta se il commit è un bug-fix
                boolean isBugFixCommit = bugCommits.containsKey(commit.getName());
                List<DiffEntry> diffs = gitService.getChangedFilesInCommit(commit);

                for (DiffEntry diff : diffs) {
                    if (diff.getChangeType() == DiffEntry.ChangeType.RENAME) continue;

                    String newPath = diff.getNewPath().replace("\\", "/");
                    String oldPath = diff.getOldPath().replace("\\", "/");

                    if (!newPath.endsWith(".java")) continue;

                    String contentAfter = gitService.getFileContentAtCommit(commit, newPath);
                    if (org.example.Main.isFileExcluded(newPath, contentAfter)) continue;

                    String contentBefore = gitService.getFileContentAtCommit(commit.getParent(0), oldPath);

                    // 1. Calcola churn a livello di FILE
                    calculateFileLevelChurn(commit, newPath, contentBefore, contentAfter, fileHistories);

                    // 2. Calcola churn a livello di METODO
                    Map<String, List<String>> stmtsBefore = getMethodStatements(contentBefore, oldPath, commit.getName());
                    Map<String, List<String>> stmtsAfter = getMethodStatements(contentAfter, newPath, commit.getName());
                    updateMethodHistoriesWithDiff(newPath, stmtsBefore, stmtsAfter, commit, methodHistories, isBugFixCommit);

                    // 3. --- LOGICA DI ASSOCIAZIONE BUG-FIX ---
                    // Se questo è un commit di fix, dobbiamo associare questo commit a tutti i metodi
                    // presenti nel file *dopo* la modifica.
                    if (isBugFixCommit) {
                        for (String signature : stmtsAfter.keySet()) {
                            String uniqueID = newPath + "/" + signature;
                            methodHistories.computeIfAbsent(uniqueID, MethodHistory::new).addFix(commit);
                        }
                    }
                    // --- FINE DELLA LOGICA REINTRODOTTA ---
                }
            } catch (org.eclipse.jgit.errors.MissingObjectException e) {
                System.err.println("[WARNING] Saltato commit " + commit.getName() + " a causa di un oggetto Git mancante.");
            }
        }
        System.out.println("Analisi storica completata.");
        return new AnalysisResult(methodHistories, fileHistories);
    }

    private void calculateFileLevelChurn(RevCommit commit, String filePath, String contentBefore, String contentAfter, Map<String, FileHistory> fileHistories) {
        List<String> fileLinesBefore = Arrays.asList(contentBefore.split("\r\n|\r|\n"));
        List<String> fileLinesAfter = Arrays.asList(contentAfter.split("\r\n|\r|\n"));

        if (fileLinesBefore.equals(fileLinesAfter)) return;

        Patch<String> filePatch = DiffUtils.diff(fileLinesBefore, fileLinesAfter);
        int linesAdded = 0;
        int linesDeleted = 0;

        for (AbstractDelta<String> delta : filePatch.getDeltas()) {
            linesAdded += delta.getTarget().getLines().size();
            linesDeleted += delta.getSource().getLines().size();
        }

        if (linesAdded > 0 || linesDeleted > 0) {
            FileHistory fh = fileHistories.computeIfAbsent(filePath, FileHistory::new);
            fh.addChange(commit, linesAdded, linesDeleted);
        }
    }

    private void updateMethodHistoriesWithDiff(String filePath, Map<String, List<String>> stmtsBefore,
                                               Map<String, List<String>> stmtsAfter, RevCommit commit,
                                               Map<String, MethodHistory> histories, boolean isBugFix) {
        Set<String> allSignatures = new HashSet<>(stmtsBefore.keySet());
        allSignatures.addAll(stmtsAfter.keySet());

        for (String signature : allSignatures) {
            List<String> linesBefore = stmtsBefore.getOrDefault(signature, Collections.emptyList());
            List<String> linesAfter = stmtsAfter.getOrDefault(signature, Collections.emptyList());

            if (linesBefore.equals(linesAfter)) continue;

            String uniqueID = filePath + "/" + signature;
            Patch<String> patch = DiffUtils.diff(linesBefore, linesAfter);
            int added = 0;
            int deleted = 0;

            for (AbstractDelta<String> delta : patch.getDeltas()) {
                added += delta.getTarget().getLines().size();
                deleted += delta.getSource().getLines().size();
            }

            if (added > 0 || deleted > 0) {
                MethodHistory history = histories.computeIfAbsent(uniqueID, MethodHistory::new);
                history.addChange(commit, added, deleted);
                // La metrica NFix è legata al churn: conta solo i fix che hanno *cambiato* il metodo.
                if (isBugFix) {
                    history.incrementFixCount();
                }
            }
        }
    }

    private Map<String, List<String>> getMethodStatements(String fileContent, String filePath, String commitHash) {
        if (fileContent == null || fileContent.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> methods = new HashMap<>();
        try {
            StaticJavaParser.parse(fileContent).findAll(MethodDeclaration.class).forEach(md -> {
                String signature = md.getSignature().asString();
                List<String> statements = md.getBody()
                        .map(body -> body.getStatements().stream()
                                .map(Statement::toString)
                                .collect(Collectors.toList()))
                        .orElse(Collections.emptyList());
                methods.put(signature, statements);
            });
        } catch (Exception | StackOverflowError e) {
            System.err.println(
                    "[PARSER_ERROR] " + e.getClass().getSimpleName()
                            + " durante il parsing di: " + filePath
                            + " (commit: " + commitHash + ")"
            );
        }
        return methods;
    }
}