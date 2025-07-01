// in src/main/java/org/example/model/MethodMetrics.java
package org.example.model;

import java.util.ArrayList;
import java.util.List;

public class MethodMetrics {
    // 5 Feature di Complessità
    private int loc;
    private int cyclomaticComplexity;
    private int parameterCount;
    private int nestingDepth;
    private int nSmells; // Placeholder

    // 5 Feature di Change
    private int methodHistories;
    private int authors;
    private int churn;
    private int maxChurn;
    private double avgChurn;

    // Converte le metriche in una lista di oggetti per il CSV
    public List<Object> toList() {
        List<Object> allMetrics = new ArrayList<>();
        // Complessità
        allMetrics.add(loc);
        allMetrics.add(cyclomaticComplexity);
        allMetrics.add(parameterCount);
        allMetrics.add(nestingDepth);
        allMetrics.add(nSmells);
        // Change
        allMetrics.add(methodHistories);
        allMetrics.add(authors);
        allMetrics.add(churn);
        allMetrics.add(maxChurn);
        allMetrics.add(avgChurn);

        return allMetrics;
    }

    // Setters
    public void setComplexityMetrics(List<Object> features) {
        this.loc = (int) features.get(0);
        this.cyclomaticComplexity = (int) features.get(1);
        this.parameterCount = (int) features.get(2);
        this.nestingDepth = (int) features.get(3);
        this.nSmells = (int) features.get(4); // Per ora sarà 0
    }

    public void setChangeMetrics(MethodHistory history) {
        this.methodHistories = history.getMethodHistories();
        this.authors = history.getAuthorsCount();
        this.churn = history.getChurn();
        this.maxChurn = history.getMaxChurn();
        this.avgChurn = history.getAvgChurn();
    }
}