// in src/main/java/org/example/model/JiraTicket.java
package org.example.model;

import java.time.ZonedDateTime; // <-- IMPORT CAMBIATO
import java.util.ArrayList;
import java.util.List;

public class JiraTicket {
    private final String key;
    // --- TIPO CAMBIATO QUI ---
    private final ZonedDateTime creationDate;
    private final ZonedDateTime resolutionDate;
    // -------------------------
    private List<String> affectedVersionsStrings;

    private Release injectedVersion;
    private Release openingVersion;
    private Release fixedVersion;

    public JiraTicket(String key, ZonedDateTime creationDate, ZonedDateTime resolutionDate) { // <-- TIPO CAMBIATO QUI
        this.key = key;
        this.creationDate = creationDate;
        this.resolutionDate = resolutionDate;
        this.affectedVersionsStrings = new ArrayList<>();
    }

    // --- GETTERS CON TIPO CAMBIATO ---
    public String getKey() { return key; }
    public ZonedDateTime getCreationDate() { return creationDate; }
    public ZonedDateTime getResolutionDate() { return resolutionDate; }
    // ---------------------------------

    public List<String> getAffectedVersionsStrings() { return affectedVersionsStrings; }
    public void setAffectedVersionsStrings(List<String> avs) { this.affectedVersionsStrings = avs; }
    public Release getInjectedVersion() { return injectedVersion; }
    public void setInjectedVersion(Release iv) { this.injectedVersion = iv; }
    public Release getOpeningVersion() { return openingVersion; }
    public void setOpeningVersion(Release ov) { this.openingVersion = ov; }
    public Release getFixedVersion() { return fixedVersion; }
    public void setFixedVersion(Release fv) { this.fixedVersion = fv; }
}