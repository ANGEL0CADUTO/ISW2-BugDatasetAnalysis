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

public class MetricsLogic {

    // Rimuovi il vecchio `calculateMetrics` e sostituiscilo con questo
    public MethodMetrics calculateMetrics(MethodData methodData, MethodHistory history) {
        MethodMetrics metrics = new MethodMetrics();
        MethodDeclaration mdNode = methodData.getDeclaration(); // Ora abbiamo di nuovo l'AST

        // Calcola e imposta le 5 feature di complessità
        int loc = mdNode.getEnd().map(p -> p.line).orElse(0) - mdNode.getBegin().map(p -> p.line).orElse(0) + 1;
        int cc = calculateCyclomaticComplexity(mdNode);
        int paramCount = mdNode.getParameters().size();
        int nesting = calculateNestingDepth(mdNode);
        int smells = 0; // Placeholder per NSmells
        metrics.setComplexityMetrics(loc, cc, paramCount, nesting, smells);

        // Imposta le 5 feature di change dalla storia
        metrics.setChangeMetrics(history);

        return metrics;
    }

    private int calculateCyclomaticComplexity(MethodDeclaration md) {
        int cc = 1;
        if (md.getBody().isPresent()) {
            BlockStmt body = md.getBody().get();
            cc += body.findAll(IfStmt.class).size();
            cc += body.findAll(ForStmt.class).size();
            cc += body.findAll(WhileStmt.class).size();
            cc += body.findAll(DoStmt.class).size();
            cc += body.findAll(SwitchEntry.class).stream().filter(se -> !se.getLabels().isEmpty()).count();
            cc += body.findAll(CatchClause.class).size();
            cc += body.findAll(ConditionalExpr.class).size();
            cc += body.findAll(BinaryExpr.class, be -> be.getOperator() == BinaryExpr.Operator.AND || be.getOperator() == BinaryExpr.Operator.OR).size();
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