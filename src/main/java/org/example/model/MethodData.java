package org.example.model;

import java.util.ArrayList;
import java.util.List;

public class MethodData {
    // Identificatori
    private final String releaseName;
    private final String methodIdentifier;

    // Target
    private String bugginess; // "YES" o "NO"

    // Metriche di Complessità (Statiche)
    private int loc;
    private int cyclomaticComplexity;

    // Metriche di Processo (Cambiamento)
    private int methodHistories; // aka NR (Num Revisions)
    private int numAuthors;
    private int stmtAdded;
    private int maxStmtAdded;
    private int avgStmtAdded;
    private int stmtDeleted; // Non richiesto esplicitamente, ma utile per il churn
    private int churn;

    public MethodData(String releaseName, String methodIdentifier) {
        this.releaseName = releaseName;
        this.methodIdentifier = methodIdentifier;
    }

    public List<Object> toCsvRow(String projectName) {
        List<Object> row = new ArrayList<>();
        row.add(projectName);
        row.add(this.releaseName);
        row.add(this.methodIdentifier);
        row.add(this.bugginess);
        row.add(this.loc);
        row.add(this.cyclomaticComplexity);
        row.add(this.methodHistories);
        row.add(this.numAuthors);
        row.add(this.stmtAdded);
        row.add(this.maxStmtAdded);
        row.add(this.avgStmtAdded);
        row.add(this.churn);
        return row;
    }

    public String getReleaseName() {
        return releaseName;
    }

    public String getMethodIdentifier() {
        return methodIdentifier;
    }

    public String getBugginess() {
        return bugginess;
    }

    public void setBugginess(String bugginess) {
        this.bugginess = bugginess;
    }

    public int getLoc() {
        return loc;
    }

    public void setLoc(int loc) {
        this.loc = loc;
    }

    public int getCyclomaticComplexity() {
        return cyclomaticComplexity;
    }

    public void setCyclomaticComplexity(int cyclomaticComplexity) {
        this.cyclomaticComplexity = cyclomaticComplexity;
    }

    public int getMethodHistories() {
        return methodHistories;
    }

    public void setMethodHistories(int methodHistories) {
        this.methodHistories = methodHistories;
    }

    public int getNumAuthors() {
        return numAuthors;
    }

    public void setNumAuthors(int numAuthors) {
        this.numAuthors = numAuthors;
    }

    public int getStmtAdded() {
        return stmtAdded;
    }

    public void setStmtAdded(int stmtAdded) {
        this.stmtAdded = stmtAdded;
    }

    public int getMaxStmtAdded() {
        return maxStmtAdded;
    }

    public void setMaxStmtAdded(int maxStmtAdded) {
        this.maxStmtAdded = maxStmtAdded;
    }

    public int getAvgStmtAdded() {
        return avgStmtAdded;
    }

    public void setAvgStmtAdded(int avgStmtAdded) {
        this.avgStmtAdded = avgStmtAdded;
    }

    public int getStmtDeleted() {
        return stmtDeleted;
    }

    public void setStmtDeleted(int stmtDeleted) {
        this.stmtDeleted = stmtDeleted;
    }

    public int getChurn() {
        return churn;
    }

    public void setChurn(int churn) {
        this.churn = churn;
    }
}