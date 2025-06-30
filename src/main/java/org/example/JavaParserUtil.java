package org.example;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.example.model.MethodInfo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JavaParserUtil {

    // --- NUOVA VERSIONE DEL METODO ---
    /**
     * Esegue il parsing del contenuto di un file Java fornito come stringa.
     * @param fileContent Il contenuto sorgente del file Java.
     * @return Una lista di metodi parsati.
     */
    public List<MethodParseResult> extractMethodsWithDeclarations(String fileContent) {
        List<MethodParseResult> results = new ArrayList<>();
        try {
            // Usa StaticJavaParser.parse(String) invece di parse(Path)
            CompilationUnit cu = StaticJavaParser.parse(fileContent);
            MethodDeclarationVisitor visitor = new MethodDeclarationVisitor();
            visitor.visit(cu, results);
        } catch (ParseProblemException | StackOverflowError e) {
            // Logga l'errore ma non bloccare l'intero processo
            // System.err.println("Errore di parsing (o StackOverflow): " + e.getMessage());
        }
        return results;
    }

    public static class MethodParseResult {
        public final MethodInfo methodInfo;
        public final MethodDeclaration methodDeclaration;
        public MethodParseResult(MethodInfo mi, MethodDeclaration md) {
            this.methodInfo = mi;
            this.methodDeclaration = md;
        }
    }

    private static class MethodDeclarationVisitor extends VoidVisitorAdapter<List<MethodParseResult>> {
        @Override
        public void visit(MethodDeclaration md, List<MethodParseResult> collector) {
            super.visit(md, collector);
            String methodName = md.getNameAsString();
            String signature = methodName + "(" +
                    md.getParameters().stream()
                            .map(p -> p.getType().asString())
                            .collect(Collectors.joining(", ")) +
                    ")";
            int startLine = md.getBegin().map(p -> p.line).orElse(-1);
            int endLine = md.getEnd().map(p -> p.line).orElse(-1);
            String body = md.getBody().map(Object::toString).orElse("");

            if (startLine != -1) {
                MethodInfo mi = new MethodInfo(methodName, signature, startLine, endLine, body);
                collector.add(new MethodParseResult(mi, md));
            }
        }
    }
}