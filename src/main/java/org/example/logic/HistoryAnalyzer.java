// in src/main/java/org/example/logic/HistoryAnalyzer.java
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
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.example.model.MethodData;
import org.example.model.MethodHistory;
import org.example.services.GitService;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class HistoryAnalyzer {

    private final GitService gitService;

    public HistoryAnalyzer(GitService gitService) {
        this.gitService = gitService;
    }

    public Map<String, MethodHistory> buildMethodsHistories(Map<String, RevCommit> bugCommits) throws GitAPIException, IOException {
        System.out.println("Inizio costruzione della storia dei metodi. Questo è il passo più lungo...");
        Map<String, MethodHistory> histories = new HashMap<>();
        // Cache per non ri-parsare lo stesso file più volte
        Map<ObjectId, Map<String, List<String>>> parsedFileCache = new HashMap<>();

        Iterable<RevCommit> allCommits = gitService.getAllCommits();

        int commitCount = 0;
        for (RevCommit commit : allCommits) {
            commitCount++;
            if (commitCount % 500 == 0) System.out.printf("Analisi commit %d...%n", commitCount);
            if (commit.getParentCount() == 0) continue;

            RevCommit parent = commit.getParent(0);
            List<DiffEntry> diffs = gitService.getChangedFilesInCommit(commit);

            for (DiffEntry diff : diffs) {
                if (!diff.getNewPath().endsWith(".java")) continue;

                // --- OTTIMIZZAZIONE MEMORIA ---
                // Estraiamo solo gli statement, non l'intero AST
                Map<String, List<String>> stmtsBefore = getMethodStatements(parent, diff.getOldPath(), parsedFileCache);
                Map<String, List<String>> stmtsAfter = getMethodStatements(commit, diff.getNewPath(), parsedFileCache);

                compareMethodStatements(diff.getNewPath(), stmtsBefore, stmtsAfter, commit, histories);
            }

            // Associa il bug fix ai metodi toccati
            if (bugCommits.containsKey(commit.getName())) {
                for (DiffEntry diff : diffs) {
                    if (diff.getNewPath().endsWith(".java")) {
                        // Rileggiamo solo le firme dei metodi, senza tenere l'AST in memoria
                        Map<String, MethodDeclaration> methodsInCommit = getMethodSignatures(commit, diff.getNewPath());
                        for (String signature : methodsInCommit.keySet()) {
                            String uniqueID = diff.getNewPath().replace("\\", "/") + "/" + signature;
                            histories.computeIfAbsent(uniqueID, MethodHistory::new).addFix(commit);
                        }
                    }
                }
            }
        }
        System.out.println("Analisi storica completata. Trovate le storie di " + histories.size() + " metodi unici.");
        return histories;
    }

    /**
     * OTTIMIZZATO: Estrae solo le liste di statement per ogni metodo, usando una cache.
     */
    private Map<String, List<String>> getMethodStatements(RevCommit commit, String filePath, Map<ObjectId, Map<String, List<String>>> cache) throws IOException {
        if ("/dev/null".equals(filePath)) return Collections.emptyMap();

        ObjectId fileId = gitService.getFileId(commit, filePath);
        if (fileId == null) return Collections.emptyMap();

        // Controlla se abbiamo già parsato questo esatto file
        if (cache.containsKey(fileId)) {
            return cache.get(fileId);
        }

        Map<String, List<String>> methodsStatements = new HashMap<>();
        String content = gitService.getFileContentAtCommit(commit, filePath);
        if (content.isEmpty()) return Collections.emptyMap();

        try {
            StaticJavaParser.parse(content).findAll(MethodDeclaration.class).forEach(md -> {
                String signature = md.getSignature().asString();
                List<String> statements = md.getBody()
                        .map(body -> body.getStatements().stream().map(Statement::toString).collect(Collectors.toList()))
                        .orElse(Collections.emptyList());
                methodsStatements.put(signature, statements);
            });
        } catch (Exception | StackOverflowError e) { /* Ignora errori di parsing su file complessi */ }

        cache.put(fileId, methodsStatements);
        return methodsStatements;
    }

    /**
     * Estrae solo le dichiarazioni dei metodi (senza corpo) per risparmiare memoria.
     */
    private Map<String, MethodDeclaration> getMethodSignatures(RevCommit commit, String filePath) throws IOException {
        Map<String, MethodDeclaration> methods = new HashMap<>();
        String content = gitService.getFileContentAtCommit(commit, filePath);
        if (content.isEmpty()) return methods;

        try {
            StaticJavaParser.parse(content).findAll(MethodDeclaration.class).forEach(md -> {
                md.setBody(null); // Rimuove il corpo per liberare memoria
                methods.put(md.getSignature().asString(), md);
            });
        } catch (Exception | StackOverflowError e) { /* Ignora errori di parsing */ }
        return methods;
    }

    private void compareMethodStatements(String filePath, Map<String, List<String>> before, Map<String, List<String>> after, RevCommit commit, Map<String, MethodHistory> histories) {
        for (Map.Entry<String, List<String>> entry : after.entrySet()) {
            String signature = entry.getKey();
            List<String> stmtsAfter = entry.getValue();
            List<String> stmtsBefore = before.getOrDefault(signature, Collections.emptyList());

            if (!stmtsAfter.equals(stmtsBefore)) {
                Patch<String> patch = DiffUtils.diff(stmtsBefore, stmtsAfter);
                int added = 0;
                int deleted = 0;
                for (AbstractDelta<String> delta : patch.getDeltas()) {
                    if (delta.getType() == DeltaType.INSERT) added += delta.getTarget().getLines().size();
                    else if (delta.getType() == DeltaType.DELETE) deleted += delta.getSource().getLines().size();
                    else if (delta.getType() == DeltaType.CHANGE) {
                        added += delta.getTarget().getLines().size();
                        deleted += delta.getSource().getLines().size();
                    }
                }

                String uniqueID = filePath.replace("\\", "/") + "/" + signature;
                // Creiamo un MethodData "fittizio" senza l'AST completo per risparmiare memoria
                MethodData dummyData = new MethodData(uniqueID, signature, commit, null);
                histories.computeIfAbsent(uniqueID, MethodHistory::new).addChange(dummyData, added, deleted);
            }
        }
    }
}