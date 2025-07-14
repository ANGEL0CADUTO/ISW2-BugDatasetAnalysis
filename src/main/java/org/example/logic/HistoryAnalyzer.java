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
import org.example.Main;
import org.example.model.FileHistory;
import org.example.model.MethodHistory;
import org.example.services.GitService;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HistoryAnalyzer {
    private static final Logger LOGGER = Logger.getLogger(HistoryAnalyzer.class.getName());

    private final GitService gitService;

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
        ParserConfiguration parserConfig = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17_PREVIEW);
        StaticJavaParser.setConfiguration(parserConfig);
    }

    public AnalysisResult analyzeCommitHistory(Map<String, RevCommit> bugCommits) throws GitAPIException, IOException {
        LOGGER.info("Inizio costruzione della storia dei metodi e dei file...");
        Map<String, MethodHistory> methodHistories = new HashMap<>();
        Map<String, FileHistory> fileHistories = new HashMap<>();

        Iterable<RevCommit> allCommits = gitService.getAllCommits();
        int commitCount = 0;

        for (RevCommit commit : allCommits) {
            commitCount++;
            if (commitCount % 500 == 0) {
                LOGGER.log(Level.INFO, "Analisi commit {0}...", commitCount);
            }

            try {
                if (commit.getParentCount() == 0) continue;

                List<DiffEntry> diffs = gitService.getChangedFilesInCommit(commit);
                for (DiffEntry diff : diffs) {
                    analyzeDiff(diff, commit, bugCommits, methodHistories, fileHistories);
                }
            } catch (org.eclipse.jgit.errors.MissingObjectException e) {
                LOGGER.log(Level.WARNING, "[WARNING] Saltato commit {0} a causa di un oggetto Git mancante.", commit.getName());
            }
        }
        LOGGER.info("Analisi storica completata.");
        return new AnalysisResult(methodHistories, fileHistories);
    }

    /**
     * Analizza una singola modifica (DiffEntry) all'interno di un commit.
     */
    private void analyzeDiff(DiffEntry diff, RevCommit commit, Map<String, RevCommit> bugCommits,
                             Map<String, MethodHistory> methodHistories, Map<String, FileHistory> fileHistories) throws IOException {

        if (diff.getChangeType() == DiffEntry.ChangeType.RENAME) return;

        String newPath = diff.getNewPath().replace("\\", "/");
        if (!newPath.endsWith(".java")) return;

        String contentAfter = gitService.getFileContentAtCommit(commit, newPath);
        if (Main.isFileExcluded(newPath, contentAfter)) return;

        String oldPath = diff.getOldPath().replace("\\", "/");
        String contentBefore = gitService.getFileContentAtCommit(commit.getParent(0), oldPath);
        boolean isBugFixCommit = bugCommits.containsKey(commit.getName());

        // 1. Calcola churn a livello di FILE
        calculateFileLevelChurn(commit, newPath, contentBefore, contentAfter, fileHistories);

        // 2. Calcola churn a livello di METODO
        Map<String, List<String>> stmtsBefore = getMethodStatements(contentBefore, oldPath, commit.getName());
        Map<String, List<String>> stmtsAfter = getMethodStatements(contentAfter, newPath, commit.getName());
        updateMethodHistoriesWithDiff(newPath, stmtsBefore, stmtsAfter, commit, methodHistories, isBugFixCommit);

        // 3. Associa il bug-fix ai metodi
        if (isBugFixCommit) {
            associateBugFixToMethods(newPath, stmtsAfter.keySet(), commit, methodHistories);
        }
    }

    /**
     * Associa un commit di fix a tutti i metodi presenti nel file modificato.
     */
    private void associateBugFixToMethods(String filePath, Set<String> signatures, RevCommit commit, Map<String, MethodHistory> histories) {
        for (String signature : signatures) {
            String uniqueID = filePath + "/" + signature;
            histories.computeIfAbsent(uniqueID, MethodHistory::new).addFix(commit);
        }
    }

    private void calculateFileLevelChurn(RevCommit commit, String filePath, String contentBefore, String contentAfter, Map<String, FileHistory> fileHistories) {
        if (contentBefore.equals(contentAfter)) return;

        List<String> fileLinesBefore = Arrays.asList(contentBefore.split("\r\n|\r|\n"));
        List<String> fileLinesAfter = Arrays.asList(contentAfter.split("\r\n|\r|\n"));

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
                // Smell 4: Sostituito con .toList()
                List<String> statements = md.getBody()
                        .map(body -> body.getStatements().stream()
                                .map(Statement::toString)
                                .toList())
                        .orElse(Collections.emptyList());
                methods.put(signature, statements);
            });
        } catch (Exception | StackOverflowError e) {
            LOGGER.log(Level.WARNING, "Errore di parsing, file saltato: {0} (commit: {1})", new Object[]{filePath, commitHash});
        }
        return methods;
    }
}