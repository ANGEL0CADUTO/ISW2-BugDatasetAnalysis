package org.example.model;

public class MethodMetrics {
    // Metriche di processo
    public int numRevisions = 0;
    public int numAuthors = 0;
    public int numFixes = 0;
    public int locAdded = 0;
    public int maxLocAdded = 0;
    public int churn = 0;
    public int maxChurn = 0;

    // Metriche statiche
    public int loc = 0;
    public int cyclomaticComplexity = 0;
}