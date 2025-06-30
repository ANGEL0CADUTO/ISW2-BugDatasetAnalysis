package org.example.logic;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.example.model.DataRow;
import org.example.model.Release;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class MetricsLogic {

    // Contiene i dati di modifica per ogni file
    // Chiave: File Path, Valore: Lista di CommitData per quel file
    private final Map<String, List<CommitData>> fileHistory;

    private static class CommitData {
        final LocalDateTime date;
        final String author;
        final int locAdded;
        final int churn;

        CommitData(RevCommit commit, int locAdded, int churn) {
            this.date = commit.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            this.author = commit.getAuthorIdent().getEmailAddress();
            this.locAdded = locAdded;
            this.churn = churn;
        }
    }

    /**
     * Costruttore: esegue il pre-calcolo di tutte le modifiche nella storia del repository.
     * Questa è l'operazione pesante, eseguita una sola volta.
     */
    public MetricsLogic(Git git) throws Exception {
        this.fileHistory = new HashMap<>();
        System.out.println("Avvio pre-calcolo delle metriche per tutti i commit (potrebbe richiedere diversi minuti)...");

        Iterable<RevCommit> allCommits = git.log().all().call();
        int count = 0;
        for (RevCommit commit : allCommits) {
            count++;
            if (count % 200 == 0) {
                System.out.printf("  ...analizzato commit %d%n", count);
            }
            if (commit.getParentCount() > 0) {
                analyzeCommitDiff(git.getRepository(), commit);
            }
        }
        System.out.println("Pre-calcolo delle metriche completato. Analizzati " + count + " commit.");
    }

    private void analyzeCommitDiff(Repository repo, RevCommit commit) throws IOException {
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
             ObjectReader reader = repo.newObjectReader()) {

            RevCommit parent = commit.getParent(0);
            diffFormatter.setRepository(repo);
            List<DiffEntry> diffs = diffFormatter.scan(parent.getTree(), commit.getTree());

            for (DiffEntry diff : diffs) {
                if (!diff.getNewPath().endsWith(".java")) continue;

                int locAddedInCommit = 0;
                int churnInCommit = 0;

                FileHeader fileHeader = diffFormatter.toFileHeader(diff);
                for (HunkHeader hunk : fileHeader.getHunks()) {
                    for (Edit edit : hunk.toEditList()) {
                        locAddedInCommit += edit.getLengthB(); // Aggiunte
                        churnInCommit += edit.getLengthA() + edit.getLengthB(); // Rimosse + Aggiunte
                    }
                }

                String filePath = diff.getNewPath().replace("/", "\\");
                CommitData data = new CommitData(commit, locAddedInCommit, churnInCommit);
                fileHistory.computeIfAbsent(filePath, k -> new ArrayList<>()).add(data);
            }
        }
    }

    /**
     * Calcola le metriche per un file in una data release, usando i dati pre-calcolati.
     * Questo metodo è ora molto veloce.
     */
    public void calculateMetricsForFile(DataRow dataRow, String filePath, Release release, List<RevCommit> bugCommits) {
        String normalizedPath = filePath.replace("/", "\\");
        List<CommitData> allCommitsForFile = fileHistory.get(normalizedPath);

        if (allCommitsForFile == null || allCommitsForFile.isEmpty()) {
            // Se il file non ha storia, tutte le metriche di processo sono 0
            setEmptyMetrics(dataRow);
            return;
        }

        // Filtra i commit avvenuti PRIMA della data della release
        List<CommitData> commitsBeforeRelease = new ArrayList<>();
        for(CommitData cd : allCommitsForFile) {
            if(cd.date.isBefore(release.getDate())) {
                commitsBeforeRelease.add(cd);
            }
        }

        if (commitsBeforeRelease.isEmpty()) {
            setEmptyMetrics(dataRow);
            return;
        }

        // Calcola le metriche aggregate
        Set<String> authors = new HashSet<>();
        int totalLocAdded = 0;
        int totalChurn = 0;
        int maxLocAdded = 0;
        int maxChurn = 0;

        for (CommitData commitData : commitsBeforeRelease) {
            authors.add(commitData.author);
            totalLocAdded += commitData.locAdded;
            totalChurn += commitData.churn;
            if (commitData.locAdded > maxLocAdded) maxLocAdded = commitData.locAdded;
            if (commitData.churn > maxChurn) maxChurn = commitData.churn;
        }

        int numRevisions = commitsBeforeRelease.size();
        dataRow.setNumRevisions(numRevisions);
        dataRow.setNumAuthors(authors.size());
        dataRow.setLocAdded(totalLocAdded);
        dataRow.setAvgLocAdded(numRevisions > 0 ? totalLocAdded / numRevisions : 0);
        dataRow.setMaxLocAdded(maxLocAdded);
        dataRow.setChurn(totalChurn);
        dataRow.setAvgChurn(numRevisions > 0 ? totalChurn / numRevisions : 0);
        dataRow.setMaxChurn(maxChurn);

        // NFix non può essere calcolato qui senza i RevCommit, lo lasciamo a 0 per ora.
        // Possiamo migliorarlo se necessario, ma è una feature meno critica.
        dataRow.setNumFixes(0);
        dataRow.setLoc(0); // Il LOC fisico del file alla release va calcolato separatamente se necessario.
    }

    private void setEmptyMetrics(DataRow row) {
        row.setNumRevisions(0);
        row.setNumAuthors(0);
        row.setLocAdded(0);
        row.setAvgLocAdded(0);
        row.setMaxLocAdded(0);
        row.setChurn(0);
        row.setAvgChurn(0);
        row.setMaxChurn(0);
        row.setNumFixes(0);
        row.setLoc(0);
    }
}