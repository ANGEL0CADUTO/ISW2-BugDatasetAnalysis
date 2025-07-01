// in src/main/java/org/example/logic/MetricsLogic.java
package org.example.logic;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.example.model.MethodData;
import org.example.model.MethodHistory;
import org.example.model.MethodMetrics;
import org.example.model.Release;
import java.util.HashSet;
import java.util.Set;

public class MetricsLogic {

    /**
     * Calcola tutte le metriche per un metodo fino a una specifica release.
     * QUESTA È LA FIRMA CORRETTA CHE IL MAIN CERCA.
     */
    public MethodMetrics calculateMetricsForRelease(MethodData methodData, MethodHistory fullHistory, Release release) {
        MethodMetrics metrics = new MethodMetrics();
        MethodDeclaration mdNode = methodData.getDeclaration();

        // 1. Calcola le metriche di complessità
        int loc = mdNode.getEnd().map(p -> p.line).orElse(0) - mdNode.getBegin().map(p -> p.line).orElse(0) + 1;
        int cc = calculateCyclomaticComplexity(mdNode);
        int paramCount = mdNode.getParameters().size();
        int nesting = calculateNestingDepth(mdNode);
        metrics.setComplexityMetrics(loc, cc, paramCount, nesting, 0); // NSmells è 0

        // 2. Calcola le metriche di change dinamicamente
        calculateDynamicChangeMetrics(metrics, fullHistory, release);

        return metrics;
    }

    private void calculateDynamicChangeMetrics(MethodMetrics metrics, MethodHistory fullHistory, Release release) {
        Set<String> authors = new HashSet<>();
        int nr = 0;
        int churn = 0;
        int maxChurn = 0;

        long releaseTime = release.getCommit().getCommitTime();

        for (MethodHistory.Change change : fullHistory.getChanges()) {
            if (change.commit.getCommitTime() <= releaseTime) {
                nr++;
                authors.add(change.commit.getAuthorIdent().getName());
                churn += change.churn;
                if (change.churn > maxChurn) {
                    maxChurn = change.churn;
                }
            }
        }

        double avgChurn = (nr == 0) ? 0 : (double) churn / nr;
        metrics.setChangeMetrics(nr, authors.size(), churn, maxChurn, avgChurn);
    }

    // Metodi helper per la complessità...
    private int calculateCyclomaticComplexity(MethodDeclaration md) {
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

    private int calculateNestingDepth(MethodDeclaration md) {
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