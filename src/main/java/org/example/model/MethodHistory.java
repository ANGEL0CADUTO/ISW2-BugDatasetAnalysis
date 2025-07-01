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

    // Campi per le metriche di change
    private int churn = 0;
    private int maxChurn = 0;

    public MethodHistory(String uniqueID) {
        this.uniqueID = uniqueID;
    }

    // Questo metodo verrà chiamato dall'HistoryAnalyzer
    public void addVersion(MethodData version, int added, int deleted) {
        this.versions.add(version);
        this.authors.add(version.getCommit().getAuthorIdent().getName());

        // Aggiorna metriche di change
        int currentChurn = added + deleted;
        this.churn += currentChurn;

        if (currentChurn > this.maxChurn) {
            this.maxChurn = currentChurn;
        }
    }

    public void addBugFixCommit(RevCommit commit) {
        this.bugFixCommits.add(commit);
    }

    // Getters per le feature di change
    public int getMethodHistories() { return this.versions.size(); }
    public int getAuthorsCount() { return this.authors.size(); }
    public int getChurn() { return this.churn; }
    public int getMaxChurn() { return this.maxChurn; }
    public double getAvgChurn() { return versions.isEmpty() ? 0 : (double) getChurn() / versions.size(); }

    public String getUniqueID() { return uniqueID; }
    public List<MethodData> getVersions() { return versions; }
    public List<RevCommit> getBugFixCommits() { return bugFixCommits; }
}