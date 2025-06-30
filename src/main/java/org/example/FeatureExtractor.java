package org.example;
// package com.yourdomain.datasetgenerator;

// import com.yourdomain.datasetgenerator.model.MethodInfo; // Assicurati package corretto
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import org.example.model.MethodInfo;


import java.util.ArrayList;
import java.util.List;

public class FeatureExtractor {

    // Nota: per calcolare features complesse, potrebbe essere meglio passare
    // l'oggetto MethodDeclaration di JavaParser invece del solo MethodInfo,
    // oppure arricchire MethodInfo con il nodo AST del metodo.
    // Qui usiamo il corpo stringa per semplicità per alcune, ma è limitante.

    public List<Object> extractFeatures(MethodInfo methodInfo, MethodDeclaration mdNode) {
        List<Object> features = new ArrayList<>();

        // 1. LOC (Lines of Code) del metodo
        features.add(methodInfo.getEndLine() - methodInfo.getStartLine() + 1);

        // 2. Cyclomatic Complexity (McCabe) - Implementazione semplificata
        //    CC = Edges - Nodes + 2P (P=1 per un singolo entry/exit point)
        //    Oppure: Decision Points + 1
        //    Decision points: if, for, while, do-while, case (in switch), catch, operatore ternario ?:, &&, ||
        int cc = 1; // Inizia da 1
        if (mdNode != null && mdNode.getBody().isPresent()) {
            BlockStmt body = mdNode.getBody().get();
            cc += body.findAll(IfStmt.class).size();
            cc += body.findAll(ForStmt.class).size();
            cc += body.findAll(WhileStmt.class).size();
            cc += body.findAll(DoStmt.class).size();
            cc += body.findAll(SwitchEntry.class).stream().filter(se -> !se.getLabels().isEmpty()).count(); // Solo case con etichette
            cc += body.findAll(CatchClause.class).size();
            cc += body.findAll(ConditionalExpr.class).size(); // operatore ternario
            // Per && e ||, contiamo le occorrenze in BinaryExpr
            cc += body.findAll(BinaryExpr.class, be ->
                    be.getOperator() == BinaryExpr.Operator.AND ||
                            be.getOperator() == BinaryExpr.Operator.OR
            ).size();
        }
        features.add(cc); // ACTIONABLE: Alta CC indica complessità, suggerisce refactoring

        // 3. Number of Parameters
        features.add(mdNode != null ? mdNode.getParameters().size() : 0);

        // 4. Number of Method Calls (all'interno di questo metodo)
        long methodCalls = 0;
        if (mdNode != null && mdNode.getBody().isPresent()) {
            methodCalls = mdNode.getBody().get().findAll(MethodCallExpr.class).size();
        }
        features.add(methodCalls);

        // Placeholder per le altre 6 features - DA IMPLEMENTARE
        // Esempi:
        // 5. Number of Local Variables (richiede analisi del corpo)
        // features.add(countLocalVariables(mdNode));
        // 6. Max Nesting Depth (richiede analisi AST del corpo)
        // features.add(calculateMaxNestingDepth(mdNode));
        // 7. Number of Return Statements
        // features.add(countReturnStatements(mdNode));
        // 8. Number of Comments (difficile con solo AST base, forse preprocessing)
        // features.add(0); // Placeholder
        // 9. Churn (richiede history Git - da calcolare esternamente e passare qui, o fare un lookup)
        // features.add(0); // Placeholder
        // 10. Qualche altra metrica... es. Halstead, o più semplicemente, numero di loop
        // features.add(countLoops(mdNode)); // for, while, do-while

        for (int i = features.size(); i < 10; i++) {
            features.add(0); // Aggiungi placeholder se non hai ancora 10 features
        }

        return features.subList(0, 10); // Assicurati di avere esattamente 10 features
    }

    // Metodi helper (da implementare se necessario per le features sopra)
    // private int countLocalVariables(MethodDeclaration mdNode) { /* ... */ return 0;}
    // private int calculateMaxNestingDepth(MethodDeclaration mdNode) { /* ... */ return 0;}
    // private int countReturnStatements(MethodDeclaration mdNode) { /* ... */ return 0;}
    // private int countLoops(MethodDeclaration mdNode) { /* ... */ return 0;}
}
