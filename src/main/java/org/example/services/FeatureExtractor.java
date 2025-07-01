// in src/main/java/org/example/services/FeatureExtractor.java
package org.example.services;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.example.model.MethodData;

import java.util.ArrayList;
import java.util.List;

public class FeatureExtractor {

    /**
     * Calcola le 5 feature di complessità per un singolo metodo.
     * @param methodData L'oggetto che rappresenta la versione del metodo.
     * @return Una lista di oggetti feature.
     */
    public List<Object> extractComplexityFeatures(MethodData methodData) {
        List<Object> features = new ArrayList<>();
        MethodDeclaration mdNode = methodData.getDeclaration();

        // 1. LOC (Lines of Code)
        int startLine = mdNode.getBegin().map(p -> p.line).orElse(0);
        int endLine = mdNode.getEnd().map(p -> p.line).orElse(0);
        features.add(endLine - startLine + 1);

        // 2. Cyclomatic Complexity (CC)
        features.add(calculateCyclomaticComplexity(mdNode));

        // 3. Parameter Count
        features.add(mdNode.getParameters().size());

        // 4. Nesting Depth
        features.add(calculateNestingDepth(mdNode));

        // 5. NSmells (placeholder)
        features.add(0);

        return features;
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

        @Override public void visit(IfStmt n, Void arg) { enterNode(); super.visit(n, arg); exitNode(); }
        @Override public void visit(ForStmt n, Void arg) { enterNode(); super.visit(n, arg); exitNode(); }
        @Override public void visit(ForEachStmt n, Void arg) { enterNode(); super.visit(n, arg); exitNode(); }
        @Override public void visit(WhileStmt n, Void arg) { enterNode(); super.visit(n, arg); exitNode(); }
        @Override public void visit(DoStmt n, Void arg) { enterNode(); super.visit(n, arg); exitNode(); }
        @Override public void visit(SwitchStmt n, Void arg) { enterNode(); super.visit(n, arg); exitNode(); }
        @Override public void visit(TryStmt n, Void arg) { enterNode(); super.visit(n, arg); exitNode(); }

        private void enterNode() {
            currentDepth++;
            maxDepth = Math.max(maxDepth, currentDepth);
        }

        private void exitNode() {
            currentDepth--;
        }

        public int getMaxDepth() {
            return maxDepth;
        }
    }
}