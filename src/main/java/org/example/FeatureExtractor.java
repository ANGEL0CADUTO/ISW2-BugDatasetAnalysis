package org.example.services;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import org.example.model.MethodInfo;

public class FeatureExtractor {

    /**
     * Calcola LOC del metodo.
     */
    public int calculateLOC(MethodInfo methodInfo) {
        if (methodInfo.getEndLine() == -1) return 0;
        return methodInfo.getEndLine() - methodInfo.getStartLine() + 1;
    }

    /**
     * Calcola la Complessità Ciclomatica di un metodo.
     */
    public int calculateCyclomaticComplexity(MethodDeclaration mdNode) {
        int cc = 1;
        if (mdNode.getBody().isPresent()) {
            BlockStmt body = mdNode.getBody().get();
            cc += body.findAll(IfStmt.class).size();
            cc += body.findAll(ForStmt.class).size();
            cc += body.findAll(WhileStmt.class).size();
            cc += body.findAll(DoStmt.class).size();
            cc += body.findAll(SwitchEntry.class).stream().filter(se -> !se.getLabels().isEmpty()).count();
            cc += body.findAll(CatchClause.class).size();
            cc += body.findAll(ConditionalExpr.class).size(); // operatore ternario
            cc += body.findAll(BinaryExpr.class, be ->
                    be.getOperator() == BinaryExpr.Operator.AND || be.getOperator() == BinaryExpr.Operator.OR
            ).size();
        }
        return cc;
    }
}