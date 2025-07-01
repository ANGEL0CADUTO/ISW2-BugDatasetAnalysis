// in src/main/java/org/example/model/MethodData.java
package org.example.model;

import com.github.javaparser.ast.body.MethodDeclaration;
import org.eclipse.jgit.revwalk.RevCommit;

public class MethodData {
    private final String uniqueID;
    private final String signature;
    private final RevCommit commit;
    private final MethodDeclaration declaration;

    public MethodData(String uniqueID, String signature, RevCommit commit, MethodDeclaration declaration) {
        this.uniqueID = uniqueID;
        this.signature = signature;
        this.commit = commit;
        this.declaration = declaration;
    }

    // Getters
    public String getUniqueID() { return uniqueID; }
    public String getSignature() { return signature; }
    public RevCommit getCommit() { return commit; }
    public MethodDeclaration getDeclaration() { return declaration; }
}