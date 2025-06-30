package org.example;



// Esempio se usi Jackson per JSON

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// package com.yourdomain.datasetgenerator;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set; // Se vuoi comunque estrarre ID per altre analisi, ma non è più il focus primario

public class CommitMessageBugIdentifier { // Rinominata per chiarezza

    // Lista di parole chiave che indicano un bug fix. Da personalizzare!
    private static final List<String> BUG_FIX_KEYWORDS = Arrays.asList(
            "fix", "fixes", "fixed", "bug", "bugfix", "defect", "correct", "correction"
            // "resolve", "resolves", "resolved" // Aggiungi con cautela, potrebbero essere usati per altro
    );

    // Pattern opzionale per identificare ID di issue, se presenti e utili per altri scopi
    // Se non hai ID di issue nei commit, puoi rimuovere questa parte.
    private final Pattern ISSUE_ID_PATTERN; // Es. BOOKKEEPER-\d+ o simili

    public CommitMessageBugIdentifier(String projectKeyPrefixForIssueIds) {
        if (projectKeyPrefixForIssueIds != null && !projectKeyPrefixForIssueIds.isEmpty()) {
            this.ISSUE_ID_PATTERN = Pattern.compile("(" + projectKeyPrefixForIssueIds.toUpperCase() + "-\\d+)", Pattern.CASE_INSENSITIVE);
        } else {
            this.ISSUE_ID_PATTERN = null; // Nessun pattern se non specificato
        }
    }

    public CommitMessageBugIdentifier() {
        this(null); // Costruttore senza project key per ID issue
    }


    /**
     * Controlla se il messaggio di commit indica un bug fix qualificato
     * basandosi su parole chiave.
     * Le specifiche "Type == defect, Status == Closed/Resolved, Resolution == Fixed"
     * sono inferite dalla presenza di queste parole chiave.
     */
    public boolean isQualifyingBugFixCommit(String commitMessage) {
        if (commitMessage == null || commitMessage.trim().isEmpty()) {
            return false;
        }
        String lowerCaseMessage = commitMessage.toLowerCase(Locale.ROOT);

        // Cerchiamo una delle parole chiave definite
        for (String keyword : BUG_FIX_KEYWORDS) {
            // Usiamo \b per matchare parole intere e non sottostringhe
            // es. "fixed" ma non "affixed"
            Pattern keywordPattern = Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b");
            if (keywordPattern.matcher(lowerCaseMessage).find()) {
                // Potresti aggiungere logica più sofisticata qui se necessario,
                // ad esempio, escludere se contiene anche "refactor" o "test" vicino.
                return true;
            }
        }
        return false;
    }

    // Questo metodo potrebbe non essere più così rilevante se non ci si basa su ID JIRA
    // ma lo lascio se per caso hai comunque ID nei commit che vuoi tracciare.
    public Set<String> extractIssueIdsFromCommitMessage(String commitMessage) {
        Set<String> ids = new java.util.HashSet<>();
        if (ISSUE_ID_PATTERN != null && commitMessage != null) {
            Matcher matcher = ISSUE_ID_PATTERN.matcher(commitMessage.toUpperCase());
            while (matcher.find()) {
                ids.add(matcher.group(1));
            }
        }
        return ids;
    }
}