package org.example.logic;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.example.model.JiraTicket;
import org.example.model.Release;
import org.example.services.GitService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

public class BugginessLogic {

    private final List<JiraTicket> tickets;
    private final List<Release> releases;
    private final Map<String, List<String>> commitTouchedFiles; // Mappa: commitHash -> lista di file toccati
    private final Map<String, List<String>> fileBuggyCycles; // Mappa: pathFile -> lista di "IV-FV"

    public BugginessLogic(List<JiraTicket> tickets, List<Release> releases, GitService gitService) throws IOException {
        this.tickets = tickets;
        this.releases = releases;
        this.commitTouchedFiles = new HashMap<>();
        this.fileBuggyCycles = new HashMap<>();

        // Esegui la logica principale al momento della costruzione
        determineBugCycles(gitService);
    }

    /**
     * Metodo principale che orchestra il calcolo del ciclo di vita dei bug.
     */
    private void determineBugCycles(GitService gitService) throws IOException {
        System.out.println("Avvio calcolo del ciclo di vita dei bug (IV, OV, FV)...");
        List<JiraTicket> ticketsWithKnownIV = new ArrayList<>();
        List<JiraTicket> ticketsWithUnknownIV = new ArrayList<>();

        // 1. Determina OV e FV e separa i ticket con e senza IV
        for (JiraTicket ticket : tickets) {
            findOpeningAndFixedVersions(ticket);
            if (ticket.getOpeningVersion() == null || ticket.getFixedVersion() == null) continue;

            findInjectedVersionFromAffected(ticket);
            if (ticket.getInjectedVersion() != null) {
                ticketsWithKnownIV.add(ticket);
            } else {
                ticketsWithUnknownIV.add(ticket);
            }
        }

        // 2. Calcola P (proportion) e stima gli IV mancanti
        double p = calculateProportion(ticketsWithKnownIV);
        estimateMissingIVs(ticketsWithUnknownIV, p);

        // 3. Popola la mappa file -> cicli di vita dei bug
        populateFileBuggyCycles(gitService);
        System.out.println("Calcolo del ciclo di vita dei bug completato.");
    }

    /**
     * Controlla se un file era "buggy" in una data release.
     */
    public boolean isBuggy(String filePath, Release currentRelease) {
        List<String> cycles = fileBuggyCycles.get(filePath.replace("/", "\\"));
        if (cycles == null) return false;

        for (String cycle : cycles) {
            String[] parts = cycle.split("-");
            int ivIndex = Integer.parseInt(parts[0]);
            int fvIndex = Integer.parseInt(parts[1]);
            int currentIndex = releases.indexOf(currentRelease);

            // Regola: IV <= v < FV
            if (currentIndex >= ivIndex && currentIndex < fvIndex) {
                return true;
            }
        }
        return false;
    }

    // --- Metodi Helper ---

    private void findOpeningAndFixedVersions(JiraTicket ticket) {
        ticket.setOpeningVersion(findReleaseByDate(ticket.getCreationDate()));
        ticket.setFixedVersion(findReleaseByDate(ticket.getResolutionDate()));
    }

    private Release findReleaseByDate(LocalDateTime date) {
        // Trova la prima release la cui data è *dopo* la data specificata
        for (Release release : releases) {
            if (release.getDate().isAfter(date)) {
                return release;
            }
        }
        return null; // Nessuna release trovata dopo quella data
    }

    private void findInjectedVersionFromAffected(JiraTicket ticket) {
        if (ticket.getAffectedVersions().isEmpty()) return;

        Release earliestIV = null;
        for (String avName : ticket.getAffectedVersions()) {
            for (Release release : releases) {
                if (release.getName().equals(avName)) {
                    if (earliestIV == null || release.getDate().isBefore(earliestIV.getDate())) {
                        earliestIV = release;
                    }
                    break;
                }
            }
        }
        ticket.setInjectedVersion(earliestIV);
    }

    private double calculateProportion(List<JiraTicket> ticketsWithKnownIV) {
        if (ticketsWithKnownIV.isEmpty()) {
            // Cold start: se non abbiamo dati, usiamo un valore di default o calcolato su altri progetti.
            // Per ora, usiamo un valore mediano comune in letteratura.
            return 0.5;
        }

        List<Double> pValues = new ArrayList<>();
        for (JiraTicket ticket : ticketsWithKnownIV) {
            double fv = releases.indexOf(ticket.getFixedVersion());
            double ov = releases.indexOf(ticket.getOpeningVersion());
            double iv = releases.indexOf(ticket.getInjectedVersion());
            if (fv > ov) { // Evita divisione per zero
                pValues.add((fv - iv) / (fv - ov));
            }
        }

        // Calcola la media dei valori di p
        return pValues.stream().mapToDouble(d -> d).average().orElse(0.5);
    }

    private void estimateMissingIVs(List<JiraTicket> ticketsWithUnknownIV, double p) {
        for (JiraTicket ticket : ticketsWithUnknownIV) {
            double fv = releases.indexOf(ticket.getFixedVersion());
            double ov = releases.indexOf(ticket.getOpeningVersion());

            int estimatedIvIndex = (int) Math.round(fv - (fv - ov) * p);
            if (estimatedIvIndex < 0) estimatedIvIndex = 0;
            if (estimatedIvIndex >= releases.size()) estimatedIvIndex = releases.size() - 1;

            ticket.setInjectedVersion(releases.get(estimatedIvIndex));
        }
    }

    private void populateFileBuggyCycles(GitService gitService) throws IOException {
        System.out.println("Associo i bug ai file modificati...");
        // Questo è il pre-calcolo per la performance!
        List<RevCommit> allBugCommits = null;
        String projectKey = "BOOKKEEPER"; // Potremmo voler passare questo dal costruttore in futuro

        try {
// Dobbiamo sapere per quale progetto stiamo lavorando
            allBugCommits = gitService.getCommitsFromTickets(getTicketKeys(), projectKey);        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }

        for (RevCommit commit : allBugCommits) {
            List<String> files = gitService.getTouchedFiles(commit);
            for (String file : files) {
                if (file.endsWith(".java")) {
                    // Trova a quale ticket corrisponde questo commit per ottenere IV e FV
                    JiraTicket associatedTicket = findTicketForCommit(commit.getFullMessage());
                    if (associatedTicket != null && associatedTicket.getInjectedVersion() != null && associatedTicket.getFixedVersion() != null) {
                        int ivIndex = releases.indexOf(associatedTicket.getInjectedVersion());
                        int fvIndex = releases.indexOf(associatedTicket.getFixedVersion());

                        String cycle = ivIndex + "-" + fvIndex;
                        fileBuggyCycles.computeIfAbsent(file.replace("/", "\\"), k -> new ArrayList<>()).add(cycle);
                    }
                }
            }
        }
    }

    private Set<String> getTicketKeys() {
        Set<String> keys = new HashSet<>();
        for (JiraTicket ticket : tickets) {
            keys.add(ticket.getKey());
        }
        return keys;
    }

    private JiraTicket findTicketForCommit(String commitMessage) {
        for (JiraTicket ticket : tickets) {
            if (commitMessage.contains(ticket.getKey())) {
                return ticket;
            }
        }
        return null;
    }
}