// in src/main/java/org/example/model/MethodMetrics.java
package org.example.model;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MethodMetrics {
    // 5 Feature di Complessità
    private int loc;
    private int cyclomaticComplexity;
    private int parameterCount;
    private int nestingDepth;
    private int nSmells;

    // 5 Feature di Change
    private int methodHistories; // NR
    private int authors;
    private int churn;
    private int maxChurn;
    private long avgChurn;

    private int nFix;

    private int classHistories;
    private int classAuthors;
    private int classChurn;
    private long avgClassChurn;

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
        allMetrics.add(nFix);

        allMetrics.add(classHistories);
        allMetrics.add(classAuthors);
        allMetrics.add(classChurn);
        allMetrics.add(avgClassChurn);

        return allMetrics;
    }

    public void setComplexityMetrics(int loc, int cc, int paramCount, int nesting, int smells) {
        this.loc = loc;
        this.cyclomaticComplexity = cc;
        this.parameterCount = paramCount;
        this.nestingDepth = nesting;
        this.nSmells = smells;
    }

    /**
     * QUESTA È LA FIRMA CORRETTA DEL METODO.
     * Accetta 5 argomenti, esattamente come viene chiamato da MetricsLogic.
     */
    public void setChangeMetrics(int nr, int nAuth, int churn, int maxChurn, long avgChurn, int nFix){
        this.methodHistories = nr;
        this.authors = nAuth;
        this.churn = churn;
        this.maxChurn = maxChurn;
        this.avgChurn = avgChurn;
        this.nFix = nFix;
    }

    // NUOVO METODO SETTER per le metriche di classe
    public void setClassChangeMetrics(int classNR, int classNAuth, int classChurn, long avgClassChurn) {
        this.classHistories = classNR;
        this.classAuthors = classNAuth;
        this.classChurn = classChurn;
        this.avgClassChurn = avgClassChurn;
    }
}