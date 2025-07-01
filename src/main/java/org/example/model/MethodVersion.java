package org.example.model;

import com.github.javaparser.ast.body.MethodDeclaration;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Rappresenta una singola "fotografia" di un metodo in un dato commit.
 */
public class MethodVersion {
    private final RevCommit commit;
    private final String filePath;
    private final MethodDeclaration methodDeclaration;

    public MethodVersion(RevCommit commit, String filePath, MethodDeclaration methodDeclaration) {
        this.commit = commit;
        this.filePath = filePath;
        this.methodDeclaration = methodDeclaration;
    }

    public RevCommit getCommit() {
        return commit;
    }

    public String getFilePath() {
        return filePath;
    }

    public MethodDeclaration getMethodDeclaration() {
        return methodDeclaration;
    }
}