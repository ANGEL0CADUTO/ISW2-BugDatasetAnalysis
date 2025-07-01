// in src/main/java/org/example/model/MethodHistory.java
package org.example.model;

import org.eclipse.jgit.revwalk.RevCommit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MethodHistory {
    private final String uniqueID;
    private final List<MethodData> versions = new ArrayList<>();
    private final Set<String> authors = new HashSet<>();
    private final List<RevCommit> bugFixCommits = new ArrayList<>();

    private int stmtAdded = 0;
    private int stmtDeleted = 0;
    private int maxStmtAdded = 0;
    private int maxStmtDeleted = 0;
    private int maxChurn = 0;
    private int condChanges = 0;
    private int elseAdded = 0;
    private int elseDeleted = 0;

    public MethodHistory(String uniqueID) {
        this.uniqueID = uniqueID;
    }

    // ...
    public void addChange(MethodData version, int added, int deleted) {
        this.versions.add(version); // 'version' potrebbe avere una declaration null
        // L'autore è ancora accessibile dal commit dentro l'oggetto version
        this.authors.add(version.getCommit().getAuthorIdent().getName());

        this.stmtAdded += added;
        this.stmtDeleted += deleted;
        int currentChurn = added + deleted;

        if (currentChurn > this.maxChurn) {
            this.maxChurn = currentChurn;
        }
    }

    public void addFix(RevCommit commit) {
        this.bugFixCommits.add(commit);
    }

    // Getters per le feature di change
    public int getMethodHistories() { return this.versions.size(); }
    public int getAuthorsCount() { return this.authors.size(); }
    public int getStmtAdded() { return stmtAdded; }
    public int getStmtDeleted() { return stmtDeleted; }
    public int getMaxStmtAdded() { return maxStmtAdded; }
    public int getMaxStmtDeleted() { return maxStmtDeleted; }
    public double getAvgStmtAdded() { return versions.isEmpty() ? 0 : (double) stmtAdded / versions.size(); }
    public double getAvgStmtDeleted() { return versions.isEmpty() ? 0 : (double) stmtDeleted / versions.size(); }
    public int getChurn() { return this.stmtAdded + this.stmtDeleted; }
    public int getMaxChurn() { return maxChurn; }
    public double getAvgChurn() { return versions.isEmpty() ? 0 : (double) getChurn() / versions.size(); }

    public List<RevCommit> getBugFixCommits() { return bugFixCommits; }
}