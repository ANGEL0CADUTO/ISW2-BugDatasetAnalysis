package org.example.model;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Objects;

public class Release implements Comparable<Release> {
    private final String name;
    private final RevCommit commit;
    private final LocalDateTime date;
    private final Ref ref; // Riferimento JGit originale al tag

    public Release(String name, RevCommit commit, Ref ref) {
        this.name = name;
        this.commit = commit;
        this.ref = ref;
        this.date = new Date((long) commit.getCommitTime() * 1000).toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public String getName() {
        return name;
    }

    public RevCommit getCommit() {
        return commit;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public Ref getRef() {
        return ref;
    }

    // Metodo per ordinare le release cronologicamente
    @Override
    public int compareTo(Release other) {
        return this.getDate().compareTo(other.getDate());
    }

    // Metodi equals e hashCode per permettere di usare l'oggetto Release in collezioni come Set o come chiave in Map
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Release release = (Release) o;
        return Objects.equals(name, release.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Release{" +
                "name='" + name + '\'' +
                ", date=" + date +
                '}';
    }
}