package org.example.model;

import java.util.Set;

public class FileMetrics {
    private final int numRevisions;
    private final int numAuthors;
    private final int locAdded;
    private final int maxLocAdded;
    private final int avgLocAdded;
    private final int churn;
    private final int maxChurn;
    private final int avgChurn;

    public FileMetrics(int numRevisions, Set<String> authors, int totalLocAdded, int maxLocAdded, int totalChurn, int maxChurn) {
        this.numRevisions = numRevisions;
        this.numAuthors = authors.size();
        this.locAdded = totalLocAdded;
        this.maxLocAdded = maxLocAdded;
        this.avgLocAdded = numRevisions > 0 ? totalLocAdded / numRevisions : 0;
        this.churn = totalChurn;
        this.maxChurn = maxChurn;
        this.avgChurn = numRevisions > 0 ? totalChurn / numRevisions : 0;
    }

    // Getters
    public int getNumRevisions() { return numRevisions; }
    public int getNumAuthors() { return numAuthors; }
    public int getLocAdded() { return locAdded; }
    public int getMaxLocAdded() { return maxLocAdded; }
    public int getAvgLocAdded() { return avgLocAdded; }
    public int getChurn() { return churn; }
    public int getMaxChurn() { return maxChurn; }
    public int getAvgChurn() { return avgChurn; }
}