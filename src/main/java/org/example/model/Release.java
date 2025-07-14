// in src/main/java/org/example/model/Release.java
package org.example.model;

import org.eclipse.jgit.revwalk.RevCommit;
import java.time.ZoneId;
import java.time.ZonedDateTime; // <-- IMPORT CAMBIATO


public class Release implements Comparable<Release> {
    private final String name;
    private final RevCommit commit;
    // --- TIPO CAMBIATO QUI ---
    private final ZonedDateTime date;
    // -------------------------
    private final int index;

    public Release(String name, RevCommit commit, int index) {
        this.name = name;
        this.commit = commit;
        this.date = ZonedDateTime.ofInstant(commit.getAuthorIdent().getWhen().toInstant(), ZoneId.systemDefault());
        this.index = index;
    }

    // --- GETTER CON TIPO CAMBIATO ---
    public String getName() { return name; }
    public RevCommit getCommit() { return commit; }
    public ZonedDateTime getDate() { return date; }
    public int getIndex() { return index; }
    // ---------------------------------

    @Override
    public int compareTo(Release other) {
        return this.getDate().compareTo(other.getDate());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Release release = (Release) obj;
        return name.equals(release.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}