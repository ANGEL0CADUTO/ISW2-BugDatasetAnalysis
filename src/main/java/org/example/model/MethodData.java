// in src/main/java/org/example/model/MethodData.java
package org.example.model;

import com.github.javaparser.ast.body.MethodDeclaration;
import org.eclipse.jgit.revwalk.RevCommit;

public class MethodData {
    private final String uniqueID; // es. path/to/File.java/methodName(params)
    private final RevCommit commit;
    private final MethodDeclaration declaration;
    private String fileContent; // Contenuto del file in cui si trova

    public MethodData(String uniqueID, RevCommit commit, MethodDeclaration declaration, String fileContent) {
        this.uniqueID = uniqueID;
        this.commit = commit;
        this.declaration = declaration;
        this.fileContent = fileContent;
    }

    // Getters
    public String getUniqueID() { return uniqueID; }
    public RevCommit getCommit() { return commit; }
    public MethodDeclaration getDeclaration() { return declaration; }
    public String getFileContent() { return fileContent; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodData that = (MethodData) o;
        return uniqueID.equals(that.uniqueID);
    }

    @Override
    public int hashCode() {
        return uniqueID.hashCode();
    }
}