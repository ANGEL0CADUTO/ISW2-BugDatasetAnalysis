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
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.example.model.FileMetrics;
import org.example.model.Release;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class MetricsLogic {

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

    // In logic/MetricsLogic.java

    private void analyzeCommitDiff(Repository repo, RevCommit commit) throws IOException {
        // LA CORREZIONE È IN QUESTA RIGA:
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
                        locAddedInCommit += edit.getLengthB();
                        churnInCommit += edit.getLengthA() + edit.getLengthB();
                    }
                }

                String filePath = diff.getNewPath().replace("/", "\\");
                CommitData data = new CommitData(commit, locAddedInCommit, churnInCommit);
                fileHistory.computeIfAbsent(filePath, k -> new ArrayList<>()).add(data);
            }
        }
    }

    /**
     * Calcola le metriche di processo per un file in una data release, usando i dati pre-calcolati.
     */
    public FileMetrics calculateMetricsForFile(String filePath, Release release) {
        String normalizedPath = filePath.replace("/", "\\");
        List<CommitData> allCommitsForFile = fileHistory.get(normalizedPath);

        if (allCommitsForFile == null || allCommitsForFile.isEmpty()) {
            return new FileMetrics(0, Collections.emptySet(), 0, 0, 0, 0);
        }

        List<CommitData> commitsBeforeRelease = new ArrayList<>();
        for(CommitData cd : allCommitsForFile) {
            if(cd.date.isBefore(release.getDate())) {
                commitsBeforeRelease.add(cd);
            }
        }

        if (commitsBeforeRelease.isEmpty()) {
            return new FileMetrics(0, Collections.emptySet(), 0, 0, 0, 0);
        }

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

        return new FileMetrics(commitsBeforeRelease.size(), authors, totalLocAdded, maxLocAdded, totalChurn, maxChurn);
    }
}