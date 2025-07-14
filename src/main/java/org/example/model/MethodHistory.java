// in src/main/java/org/example/model/MethodHistory.java
package org.example.model;

import org.eclipse.jgit.revwalk.RevCommit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MethodHistory {
    private final String uniqueID;
    private final List<Change> changes = new ArrayList<>();
    private final List<RevCommit> bugFixCommits = new ArrayList<>();

    private int nFix = 0;


    public static class Change {
        public final RevCommit commit;
        public final int churn;

        public Change(RevCommit commit, int churn) {
            this.commit = commit;
            this.churn = churn;
        }
    }

    public MethodHistory(String uniqueID) {
        this.uniqueID = uniqueID;
    }





    public void incrementFixCount() {
        this.nFix++;
    }


    public int getNFix() {
        return this.nFix;
    }
    public void addChange(RevCommit commit, int addedStmts, int deletedStmts) {
        int currentChurn = addedStmts + deletedStmts;
        if (currentChurn > 0) {
            this.changes.add(new Change(commit, currentChurn));
        }
    }

    public void addFix(RevCommit commit) {
        this.bugFixCommits.add(commit);
    }

    // --- METODO REINTRODOTTO E CORRETTO ---
    public int getMethodHistories() {
        return this.changes.size();
    }

    public List<Change> getChanges() { return changes; }
    public List<RevCommit> getBugFixCommits() { return bugFixCommits; }
}