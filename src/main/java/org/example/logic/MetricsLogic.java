// in src/main/java/org/example/logic/MetricsLogic.java
package org.example.logic;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.example.model.*;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional; // Aggiungi questo import

public class MetricsLogic {

    /**
     * Classe contenitore interna per aggregare i risultati durante il calcolo
     * delle metriche di change, riducendo la complessità dei metodi.
     */
    private static class ChangeMetricsAccumulator {
        int cumulativeNR = 0;
        Set<String> cumulativeAuthors = new HashSet<>();
        double weightedChurn = 0.0;
        int maxChurn = 0;
        int cumulativeNFix = 0;
    }

    public MethodMetrics calculateMetricsForRelease(MethodData methodData, MethodHistory methodHistory, FileHistory fileHistory,
                                                    Release currentRelease, int nSmells, List<Release> allReleases,
                                                    int totalReleasesCount) {
        MethodMetrics metrics = new MethodMetrics();
        MethodDeclaration mdNode = methodData.getDeclaration();

        int loc = mdNode.getEnd().map(p -> p.line).orElse(0) - mdNode.getBegin().map(p -> p.line).orElse(0) + 1;
        int cc = calculateCyclomaticComplexity(mdNode);
        int paramCount = mdNode.getParameters().size();
        int nesting = calculateNestingDepth(mdNode);
        metrics.setComplexityMetrics(loc, cc, paramCount, nesting, nSmells);

        calculateMethodChangeMetrics(metrics, methodHistory, currentRelease, allReleases, totalReleasesCount);
        calculateClassChangeMetrics(metrics, fileHistory, currentRelease, allReleases, totalReleasesCount);

        return metrics;
    }

    private void calculateMethodChangeMetrics(MethodMetrics metrics, MethodHistory methodHistory, Release currentRelease,
                                              List<Release> allReleases, int totalReleasesCount) {
        if (methodHistory == null) {
            metrics.setChangeMetrics(0, 0, 0, 0, 0, 0);
            return;
        }

        ChangeMetricsAccumulator accumulator = new ChangeMetricsAccumulator();
        long releaseTime = currentRelease.getCommit().getCommitTime();

        for (MethodHistory.Change change : methodHistory.getChanges()) {
            if (change.commit.getCommitTime() <= releaseTime) {
                updateAccumulatorForChange(accumulator, change, methodHistory, currentRelease, allReleases, totalReleasesCount);
            }
        }

        int totalWeightedChurn = (int) Math.round(accumulator.weightedChurn);
        long avgWeightedChurn = (accumulator.cumulativeNR == 0) ? 0 : Math.round(accumulator.weightedChurn / accumulator.cumulativeNR);

        metrics.setChangeMetrics(accumulator.cumulativeNR, accumulator.cumulativeAuthors.size(), totalWeightedChurn,
                accumulator.maxChurn, avgWeightedChurn, accumulator.cumulativeNFix);
    }

    /**
     * Aggiorna l'accumulatore delle metriche per un singolo cambiamento.
     */
    private void updateAccumulatorForChange(ChangeMetricsAccumulator acc, MethodHistory.Change change, MethodHistory history,
                                            Release currentRelease, List<Release> allReleases, int totalReleasesCount) {
        acc.cumulativeNR++;
        acc.cumulativeAuthors.add(change.commit.getAuthorIdent().getName());
        if (change.churn > acc.maxChurn) {
            acc.maxChurn = change.churn;
        }

        // Calcolo del peso temporale
        Release changeRelease = findReleaseForCommit(change.commit, allReleases);
        int changeReleaseIndex = (changeRelease != null) ? changeRelease.getIndex() : 0;
        int ageInReleases = currentRelease.getIndex() - changeReleaseIndex;
        double weight = 1.0 - ((double) ageInReleases / totalReleasesCount);

        if (weight > 0) {
            acc.weightedChurn += (change.churn * weight);
        }

        if (history.getBugFixCommits().stream().anyMatch(fixCommit -> fixCommit.equals(change.commit))) {
            acc.cumulativeNFix++;
        }
    }

    private void calculateClassChangeMetrics(MethodMetrics metrics, FileHistory fileHistory, Release currentRelease,
                                             List<Release> allReleases, int totalReleasesCount) {
        if (fileHistory == null) {
            metrics.setClassChangeMetrics(0, 0, 0, 0);
            return;
        }

        int classNR = 0;
        Set<String> classAuthors = new HashSet<>();
        double weightedClassChurn = 0.0;
        long releaseTime = currentRelease.getCommit().getCommitTime();

        for (MethodHistory.Change change : fileHistory.getChanges()) {
            if (change.commit.getCommitTime() <= releaseTime) {
                classNR++;
                classAuthors.add(change.commit.getAuthorIdent().getName());

                Release changeRelease = findReleaseForCommit(change.commit, allReleases);
                int changeReleaseIndex = (changeRelease != null) ? changeRelease.getIndex() : 0;
                int ageInReleases = currentRelease.getIndex() - changeReleaseIndex;
                double weight = 1.0 - ((double) ageInReleases / totalReleasesCount);

                if (weight > 0) {
                    weightedClassChurn += (change.churn * weight);
                }
            }
        }

        long avgClassChurn = (classNR == 0) ? 0 : Math.round(weightedClassChurn / classNR);
        metrics.setClassChangeMetrics(classNR, classAuthors.size(), (int) Math.round(weightedClassChurn), avgClassChurn);
    }

    private Release findReleaseForCommit(RevCommit commit, List<Release> allReleases) {
        long commitTime = commit.getCommitTime();
        return allReleases.stream()
                .filter(r -> r.getCommit().getCommitTime() >= commitTime)
                .min(Comparator.comparingLong(r -> r.getCommit().getCommitTime()))
                .orElse(null);
    }

    /**
     * Calcola la Complessità Ciclomatica di un metodo.
     * Risolve lo smell dell'accesso a Optional senza isPresent().
     */
    public int calculateCyclomaticComplexity(MethodDeclaration md) {
        int cc = 1;
        // Controlla se il corpo del metodo esiste prima di accedervi.
        Optional<BlockStmt> bodyOpt = md.getBody();
        if (bodyOpt.isPresent()) {
            BlockStmt body = bodyOpt.get();
            cc += body.findAll(IfStmt.class).size();
            cc += body.findAll(ForStmt.class).size();
            cc += body.findAll(WhileStmt.class).size();
            cc += body.findAll(DoStmt.class).size();
            cc += body.findAll(SwitchEntry.class, se -> !se.getLabels().isEmpty()).size();
            cc += body.findAll(CatchClause.class).size();
            cc += body.findAll(ConditionalExpr.class).size();
            cc += body.findAll(BinaryExpr.class, be -> be.getOperator() == BinaryExpr.Operator.AND || be.getOperator() == BinaryExpr.Operator.OR).size();
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