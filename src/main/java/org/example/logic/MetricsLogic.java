// in src/main/java/org/example/logic/MetricsLogic.java
package org.example.logic;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.example.model.*; // Importa tutti i modelli, inclusi i nuovi

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MetricsLogic {

    /**
     * Entry point per il calcolo delle metriche.
     * Ora accetta sia la storia del metodo (MethodHistory) che quella del file (FileHistory)
     * per calcolare un set di feature più ricco e contestuale.
     */
    public MethodMetrics calculateMetricsForRelease(MethodData methodData, MethodHistory methodHistory, FileHistory fileHistory,
                                                    Release currentRelease, int nSmells, List<Release> allReleases,
                                                    int totalReleasesCount) {
        MethodMetrics metrics = new MethodMetrics();
        MethodDeclaration mdNode = methodData.getDeclaration();

        // 1. Calcola le metriche di complessità statica (logica invariata)
        int loc = mdNode.getEnd().map(p -> p.line).orElse(0) - mdNode.getBegin().map(p -> p.line).orElse(0) + 1;
        int cc = calculateCyclomaticComplexity(mdNode);
        int paramCount = mdNode.getParameters().size();
        int nesting = calculateNestingDepth(mdNode);
        metrics.setComplexityMetrics(loc, cc, paramCount, nesting, nSmells);

        // 2. Calcola le metriche di "change" a livello di METODO
        calculateMethodChangeMetrics(metrics, methodHistory, currentRelease, allReleases, totalReleasesCount);

        // 3. Calcola le nuove metriche di "change" a livello di CLASSE
        calculateClassChangeMetrics(metrics, fileHistory, currentRelease, allReleases, totalReleasesCount);

        return metrics;
    }

    /**
     * Calcola le metriche di "change" specifiche del METODO (NR, NAuth, Churn, etc.).
     * Usa un approccio cumulativo con pesatura temporale.
     */
    private void calculateMethodChangeMetrics(MethodMetrics metrics, MethodHistory methodHistory, Release currentRelease,
                                              List<Release> allReleases, int totalReleasesCount) {

        // Se non c'è una storia per questo metodo, tutte le sue metriche di change sono 0.
        if (methodHistory == null) {
            metrics.setChangeMetrics(0, 0, 0, 0, 0, 0);
            return;
        }

        int cumulativeNR = 0;
        Set<String> cumulativeAuthors = new HashSet<>();
        double weightedChurn = 0.0;
        int maxChurn = 0;
        int cumulativeNFix = 0;
        int currentReleaseIndex = currentRelease.getIndex();

        for (MethodHistory.Change change : methodHistory.getChanges()) {
            if (change.commit.getCommitTime() <= currentRelease.getCommit().getCommitTime()) {
                cumulativeNR++;
                cumulativeAuthors.add(change.commit.getAuthorIdent().getName());
                if (change.churn > maxChurn) {
                    maxChurn = change.churn;
                }

                Release changeRelease = findReleaseForCommit(change.commit, allReleases);
                int changeReleaseIndex = (changeRelease != null) ? changeRelease.getIndex() : 0;
                int ageInReleases = currentReleaseIndex - changeReleaseIndex;
                double weight = 1.0 - ((double) ageInReleases / totalReleasesCount);

                if (weight > 0) {
                    weightedChurn += (change.churn * weight);
                }

                if (methodHistory.getBugFixCommits().stream().anyMatch(fixCommit -> fixCommit.equals(change.commit))) {
                    cumulativeNFix++;
                }
            }
        }

        int totalWeightedChurn = (int) Math.round(weightedChurn);
        long avgWeightedChurn = (cumulativeNR == 0) ? 0 : Math.round(weightedChurn / cumulativeNR);

        metrics.setChangeMetrics(cumulativeNR, cumulativeAuthors.size(), totalWeightedChurn,
                maxChurn, avgWeightedChurn, cumulativeNFix);
    }

    /**
     * NUOVO METODO: Calcola le metriche di "change" a livello di CLASSE (File).
     * Queste metriche catturano l'instabilità dell'intero file.
     */
    private void calculateClassChangeMetrics(MethodMetrics metrics, FileHistory fileHistory, Release currentRelease,
                                             List<Release> allReleases, int totalReleasesCount) {

        // Se non c'è una storia per questo file, tutte le sue metriche di change sono 0.
        if (fileHistory == null) {
            metrics.setClassChangeMetrics(0, 0, 0,0);
            return;
        }

        int classNR = 0;
        Set<String> classAuthors = new HashSet<>();
        double weightedClassChurn = 0.0;
        int currentReleaseIndex = currentRelease.getIndex();

        for (MethodHistory.Change change : fileHistory.getChanges()) {
            if (change.commit.getCommitTime() <= currentRelease.getCommit().getCommitTime()) {
                classNR++;
                classAuthors.add(change.commit.getAuthorIdent().getName());

                Release changeRelease = findReleaseForCommit(change.commit, allReleases);
                int changeReleaseIndex = (changeRelease != null) ? changeRelease.getIndex() : 0;
                int ageInReleases = currentReleaseIndex - changeReleaseIndex;
                double weight = 1.0 - ((double) ageInReleases / totalReleasesCount);

                if (weight > 0) {
                    weightedClassChurn += (change.churn * weight);
                }
            }
        }
        long avgClassChurn = (classNR == 0) ? 0 : Math.round(weightedClassChurn / classNR);

        metrics.setClassChangeMetrics(classNR, classAuthors.size(), (int) Math.round(weightedClassChurn), avgClassChurn);
    }

    /**
     * Metodo di supporto per trovare la prima release pubblicata dopo (o durante) un dato commit.
     */
    private Release findReleaseForCommit(RevCommit commit, List<Release> allReleases) {
        long commitTime = commit.getCommitTime();
        return allReleases.stream()
                .filter(r -> r.getCommit().getCommitTime() >= commitTime)
                .min(Comparator.comparingLong(r -> r.getCommit().getCommitTime()))
                .orElse(null);
    }

    // --- Metodi per il calcolo della complessità (invariati) ---
    public int calculateCyclomaticComplexity(MethodDeclaration md) {
        int cc = 1;
        if (md.getBody().isPresent()) {
            cc += md.getBody().get().findAll(IfStmt.class).size();
            cc += md.getBody().get().findAll(ForStmt.class).size();
            cc += md.getBody().get().findAll(WhileStmt.class).size();
            cc += md.getBody().get().findAll(DoStmt.class).size();
            cc += md.getBody().get().findAll(SwitchEntry.class, se -> !se.getLabels().isEmpty()).size();
            cc += md.getBody().get().findAll(CatchClause.class).size();
            cc += md.getBody().get().findAll(ConditionalExpr.class).size();
            cc += md.getBody().get().findAll(BinaryExpr.class, be -> be.getOperator() == BinaryExpr.Operator.AND || be.getOperator() == BinaryExpr.Operator.OR).size();
        }
        return cc;
    }

    public int calculateNestingDepth(MethodDeclaration md) {
        NestingVisitor visitor = new NestingVisitor();
        md.accept(visitor, null);
        return visitor.getMaxDepth();
    }

    private static class NestingVisitor extends VoidVisitorAdapter<Void> {
        private int maxDepth = 0;
        private int currentDepth = 0;
        private void enter() { currentDepth++; maxDepth = Math.max(maxDepth, currentDepth); }
        private void exit() { currentDepth--; }
        @Override public void visit(IfStmt n, Void arg) { enter(); super.visit(n, arg); exit(); }
        @Override public void visit(ForStmt n, Void arg) { enter(); super.visit(n, arg); exit(); }
        @Override public void visit(ForEachStmt n, Void arg) { enter(); super.visit(n, arg); exit(); }
        @Override public void visit(WhileStmt n, Void arg) { enter(); super.visit(n, arg); exit(); }
        @Override public void visit(DoStmt n, Void arg) { enter(); super.visit(n, arg); exit(); }
        @Override public void visit(SwitchStmt n, Void arg) { enter(); super.visit(n, arg); exit(); }
        @Override public void visit(TryStmt n, Void arg) { enter(); super.visit(n, arg); exit(); }
        public int getMaxDepth() { return maxDepth; }
    }
}