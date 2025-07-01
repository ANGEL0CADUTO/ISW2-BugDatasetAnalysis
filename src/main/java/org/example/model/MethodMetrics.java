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
    private int methodHistories; // NR
    private int authors;
    private int churn;
    private int maxChurn;
    private double avgChurn;

    public List<Object> toList() {
        List<Object> allMetrics = new ArrayList<>();
        allMetrics.add(loc);
        allMetrics.add(cyclomaticComplexity);
        allMetrics.add(parameterCount);
        allMetrics.add(nestingDepth);
        allMetrics.add(nSmells);
        allMetrics.add(methodHistories);
        allMetrics.add(authors);
        allMetrics.add(churn);
        allMetrics.add(maxChurn);
        allMetrics.add(avgChurn);
        return allMetrics;
    }

    // Setters
    public void setComplexityMetrics(int loc, int cc, int paramCount, int nesting, int smells) {
        this.loc = loc;
        this.cyclomaticComplexity = cc;
        this.parameterCount = paramCount;
        this.nestingDepth = nesting;
        this.nSmells = smells;
    }

    public void setChangeMetrics(MethodHistory history) {
        this.methodHistories = history.getMethodHistories();
        this.authors = history.getAuthorsCount();
        this.churn = history.getChurn();
        this.maxChurn = history.getMaxChurn();
        this.avgChurn = history.getAvgChurn();
    }
}