package org.example.model;

import java.util.ArrayList;
import java.util.List;

public class DataRow {
    // Colonne identificative
    private String releaseName;
    private String methodName; // Conterrà il path completo del file + la firma

    // Colonna target
    private String bugginess; // "yes" o "no"

    // Feature calcolate
    private int loc;
    private int locAdded;
    private int avgLocAdded;
    private int maxLocAdded;
    private int churn;
    private int avgChurn;
    private int maxChurn;
    private int numAuthors;
    private int numRevisions;
    private int numFixes; // Aggiunta: numero di fix che hanno toccato il file

    public DataRow(String releaseName, String methodName) {
        this.releaseName = releaseName;
        this.methodName = methodName;
    }

    public List<Object> toCsvRow(String projectName) {
        List<Object> row = new ArrayList<>();
        row.add(projectName);
        row.add(this.releaseName);
        row.add(this.methodName);
        row.add(this.bugginess);

        // Aggiungi le feature nell'ordine corretto
        row.add(this.loc);
        row.add(this.locAdded);
        row.add(this.avgLocAdded);
        row.add(this.maxLocAdded);
        row.add(this.churn);
        row.add(this.avgChurn);
        row.add(this.maxChurn);
        row.add(this.numAuthors);
        row.add(this.numRevisions);
        row.add(this.numFixes);

        return row;
    }

    // --- Getter e Setter per tutte le proprietà ---

    public String getReleaseName() { return releaseName; }
    public String getMethodName() { return methodName; }
    public String getBugginess() { return bugginess; }
    public void setBugginess(String bugginess) { this.bugginess = bugginess; }
    public int getLoc() { return loc; }
    public void setLoc(int loc) { this.loc = loc; }
    public int getLocAdded() { return locAdded; }
    public void setLocAdded(int locAdded) { this.locAdded = locAdded; }
    public int getAvgLocAdded() { return avgLocAdded; }
    public void setAvgLocAdded(int avgLocAdded) { this.avgLocAdded = avgLocAdded; }
    public int getMaxLocAdded() { return maxLocAdded; }
    public void setMaxLocAdded(int maxLocAdded) { this.maxLocAdded = maxLocAdded; }
    public int getChurn() { return churn; }
    public void setChurn(int churn) { this.churn = churn; }
    public int getAvgChurn() { return avgChurn; }
    public void setAvgChurn(int avgChurn) { this.avgChurn = avgChurn; }
    public int getMaxChurn() { return maxChurn; }
    public void setMaxChurn(int maxChurn) { this.maxChurn = maxChurn; }
    public int getNumAuthors() { return numAuthors; }
    public void setNumAuthors(int numAuthors) { this.numAuthors = numAuthors; }
    public int getNumRevisions() { return numRevisions; }
    public void setNumRevisions(int numRevisions) { this.numRevisions = numRevisions; }
    public int getNumFixes() { return numFixes; }
    public void setNumFixes(int numFixes) { this.numFixes = numFixes; }
}