package org.example.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class JiraTicket {
    private final String key; // Es. "BOOKKEEPER-1234"
    private final LocalDateTime creationDate;
    private final LocalDateTime resolutionDate;
    private final List<String> affectedVersions; // Nomi delle versioni affette, da JIRA

    // Queste verranno popolate in seguito dalla nostra logica
    private Release injectedVersion;
    private Release openingVersion;
    private Release fixedVersion;

    public JiraTicket(String key, LocalDateTime creationDate, LocalDateTime resolutionDate) {
        this.key = key;
        this.creationDate = creationDate;
        this.resolutionDate = resolutionDate;
        this.affectedVersions = new ArrayList<>();
    }

    public String getKey() {
        return key;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public LocalDateTime getResolutionDate() {
        return resolutionDate;
    }

    public List<String> getAffectedVersions() {
        return affectedVersions;
    }

    public void addAffectedVersion(String version) {
        this.affectedVersions.add(version);
    }

    // Getter e Setter per le versioni calcolate
    public Release getInjectedVersion() { return injectedVersion; }
    public void setInjectedVersion(Release injectedVersion) { this.injectedVersion = injectedVersion; }

    public Release getOpeningVersion() { return openingVersion; }
    public void setOpeningVersion(Release openingVersion) { this.openingVersion = openingVersion; }

    public Release getFixedVersion() { return fixedVersion; }
    public void setFixedVersion(Release fixedVersion) { this.fixedVersion = fixedVersion; }
}