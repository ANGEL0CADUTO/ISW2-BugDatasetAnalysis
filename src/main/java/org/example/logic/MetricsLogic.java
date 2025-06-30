package org.example.logic;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.example.model.DataRow;
import org.example.model.Release;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MetricsLogic {

    private final Git git;

    public MetricsLogic(Git git) {
        this.git = git;
    }

    /**
     * Popola l'oggetto DataRow con tutte le feature calcolate per un file fino a una data release.
     */
    public void calculateMetricsForFile(DataRow dataRow, String filePath, Release release, List<RevCommit> bugCommits) throws IOException, GitAPIException {
        // Ottieni la storia del file fino alla release corrente
        List<RevCommit> commits = getCommitsTouchingFile(filePath, release.getCommit());

        if (commits.isEmpty()) return;

        dataRow.setNumRevisions(commits.size());

        calculateSizeAndChurnMetrics(dataRow, commits, filePath);
        calculateAuthorAndFixMetrics(dataRow, commits, bugCommits);
    }

    /**
     * Ottiene la lista dei commit che hanno modificato un file, fino a un certo commit.
     */
    private List<RevCommit> getCommitsTouchingFile(String filePath, RevCommit endCommit) throws GitAPIException, IncorrectObjectTypeException, MissingObjectException {
        List<RevCommit> commitList = new ArrayList<>();
        git.log().add(endCommit.getId()).addPath(filePath).call().forEach(commitList::add);
        return commitList;
    }

    /**
     * Calcola le metriche basate sul numero di autori e di bug fix.
     */
    private void calculateAuthorAndFixMetrics(DataRow dataRow, List<RevCommit> commits, List<RevCommit> bugCommits) {
        Set<String> authors = new HashSet<>();
        int fixCount = 0;

        Set<ObjectId> bugCommitIds = new HashSet<>();
        for (RevCommit bc : bugCommits) {
            bugCommitIds.add(bc.getId());
        }

        for (RevCommit commit : commits) {
            authors.add(commit.getAuthorIdent().getEmailAddress());
            if (bugCommitIds.contains(commit.getId())) {
                fixCount++;
            }
        }

        dataRow.setNumAuthors(authors.size());
        dataRow.setNumFixes(fixCount);
    }

    /**
     * Calcola LOC, LOC Added, e Churn analizzando i diff di ogni commit.
     */
    private void calculateSizeAndChurnMetrics(DataRow dataRow, List<RevCommit> commits, String filePath) throws IOException {
        int totalLocAdded = 0;
        int totalChurn = 0;
        int maxLocAdded = 0;
        int maxChurn = 0;

        for (RevCommit commit : commits) {
            if (commit.getParentCount() == 0) continue;

            int locAddedInCommit = 0;
            int churnInCommit = 0;

            try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
                 ObjectReader reader = git.getRepository().newObjectReader()) {

                RevCommit parent = commit.getParent(0);
                diffFormatter.setRepository(git.getRepository());
                List<DiffEntry> diffs = diffFormatter.scan(parent.getTree(), commit.getTree());

                for (DiffEntry diff : diffs) {
                    if (diff.getNewPath().equals(filePath)) {
                        FileHeader fileHeader = diffFormatter.toFileHeader(diff);
                        for (HunkHeader hunk : fileHeader.getHunks()) {
                            for (Edit edit : hunk.toEditList()) {
                                locAddedInCommit += edit.getLengthB(); // Linee aggiunte
                                churnInCommit += edit.getLengthA() + edit.getLengthB(); // Linee rimosse + aggiunte
                            }
                        }
                    }
                }
            }

            totalLocAdded += locAddedInCommit;
            totalChurn += churnInCommit;
            if (locAddedInCommit > maxLocAdded) maxLocAdded = locAddedInCommit;
            if (churnInCommit > maxChurn) maxChurn = churnInCommit;
        }

        // Calcolo finale delle metriche
        int numCommits = commits.size();
        dataRow.setLocAdded(totalLocAdded);
        dataRow.setAvgLocAdded(numCommits > 0 ? totalLocAdded / numCommits : 0);
        dataRow.setMaxLocAdded(maxLocAdded);

        dataRow.setChurn(totalChurn);
        dataRow.setAvgChurn(numCommits > 0 ? totalChurn / numCommits : 0);
        dataRow.setMaxChurn(maxChurn);

        // Per il LOC finale, dobbiamo leggere il file in quella revisione
        // Per semplicità per ora, lo omettiamo, dato che le altre sono metriche di processo.
        // Se necessario, aggiungeremo un metodo a GitService per leggere il LOC di un file a un commit.
        dataRow.setLoc(0); // Placeholder
    }
}