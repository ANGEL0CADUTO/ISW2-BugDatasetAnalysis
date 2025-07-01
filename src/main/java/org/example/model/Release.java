// in src/main/java/org/example/model/Release.java
package org.example.model;

import org.eclipse.jgit.revwalk.RevCommit;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class Release {
    private final String name;
    private final RevCommit commit;
    private final LocalDateTime date;
    private final int index;

    public Release(String name, RevCommit commit, int index) {
        this.name = name;
        this.commit = commit;
        this.date = LocalDateTime.ofInstant(commit.getAuthorIdent().getWhen().toInstant(), ZoneId.systemDefault());
        this.index = index;
    }

    // Getters
    public String getName() { return name; }
    public RevCommit getCommit() { return commit; }
    public LocalDateTime getDate() { return date; }
    public int getIndex() { return index; }
}