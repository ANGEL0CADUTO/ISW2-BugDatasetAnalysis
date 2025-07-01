// in src/main/java/org/example/logic/BugginessLogic.java
package org.example.logic;

import org.example.model.JiraTicket;
import org.example.model.Release;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BugginessLogic {
    private final List<Release> releases;

    public BugginessLogic(List<Release> releases) {
        this.releases = releases;
    }

    /**
     * Metodo principale che orchestra il calcolo del ciclo di vita dei bug usando Proportion.
     */
    public void calculateBugLifecycles(List<JiraTicket> tickets) {
        System.out.println("Avvio calcolo del ciclo di vita dei bug (IV, OV, FV) con Proportion...");
        List<JiraTicket> ticketsWithKnownIV = new ArrayList<>();
        List<JiraTicket> ticketsWithUnknownIV = new ArrayList<>();

        // 1. Determina OV, FV e IV (se possibile) per ogni ticket
        for (JiraTicket ticket : tickets) {
            setOpeningAndFixedVersions(ticket);
            // Processa solo ticket per cui possiamo trovare OV e FV
            if (ticket.getOpeningVersion() != null && ticket.getFixedVersion() != null) {
                setInjectedVersionFromAffected(ticket); // Prova a trovare IV dal campo "Affected Versions"
                if (ticket.getInjectedVersion() != null) {
                    ticketsWithKnownIV.add(ticket);
                } else {
                    ticketsWithUnknownIV.add(ticket);
                }
            }
        }
        System.out.println("Trovati " + ticketsWithKnownIV.size() + " ticket con IV nota e " + ticketsWithUnknownIV.size() + " con IV da stimare.");

        // 2. Calcola P (proportion) e stima gli IV mancanti
        double p = calculateProportion(ticketsWithKnownIV);
        System.out.println("Valore di Proportion (p) calcolato: " + p);
        estimateMissingIVs(ticketsWithUnknownIV, p);

        System.out.println("Calcolo del ciclo di vita dei bug completato.");
    }

    private void setOpeningAndFixedVersions(JiraTicket ticket) {
        // La OV è la prima release dopo la data di creazione del ticket
        ticket.setOpeningVersion(findReleaseByDate(ticket.getCreationDate()));
        // La FV è la prima release dopo la data di risoluzione del ticket
        ticket.setFixedVersion(findReleaseByDate(ticket.getResolutionDate()));
    }

    private Release findReleaseByDate(LocalDateTime date) {
        if (date == null) return null;
        return releases.stream()
                .filter(r -> r.getDate().isAfter(date))
                .findFirst()
                .orElse(null);
    }

    private void setInjectedVersionFromAffected(JiraTicket ticket) {
        if (ticket.getAffectedVersionsStrings().isEmpty()) return;

        // Trova la release più vecchia tra quelle listate in "Affected Versions"
        ticket.getAffectedVersionsStrings().stream()
                .map(this::findReleaseByName)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.comparing(Release::getDate))
                .ifPresent(ticket::setInjectedVersion);
    }

    private Release findReleaseByName(String name) {
        return releases.stream()
                .filter(r -> r.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    private double calculateProportion(List<JiraTicket> ticketsWithKnownIV) {
        if (ticketsWithKnownIV.isEmpty()) {
            System.out.println("Nessun ticket con IV nota. Uso valore di default per Cold Start (p=0.5).");
            return 0.5; // Valore di default per Cold Start
        }

        List<Double> pValues = ticketsWithKnownIV.stream()
                .map(ticket -> {
                    double fv = ticket.getFixedVersion().getIndex();
                    double ov = ticket.getOpeningVersion().getIndex();
                    double iv = ticket.getInjectedVersion().getIndex();
                    if (fv > ov) {
                        return (fv - iv) / (fv - ov);
                    }
                    return -1.0; // Valore non valido da scartare
                })
                .filter(p -> p >= 0)
                .collect(Collectors.toList());

        if (pValues.isEmpty()) return 0.5; // Nessun valore valido

        return pValues.stream().mapToDouble(d -> d).average().orElse(0.5);
    }

    /**
     * Determina se un metodo era "buggy" in una data release usando la logica Post-Release.
     * Un metodo è buggy in Release R se è stato modificato da un bug-fix commit
     * DOPO il commit della Release R.
     * @param history La storia completa del metodo.
     * @param currentRelease La release per cui stiamo calcolando la bugginess.
     * @return true se il metodo è considerato buggy, false altrimenti.
     */
    public boolean isMethodBuggyInRelease(MethodHistory history, Release currentRelease) {
        if (history.getBugFixCommits().isEmpty()) {
            return false;
        }

        long releaseTime = currentRelease.getCommit().getCommitTime();

        // Controlla se esiste almeno un bug-fix commit nella storia del metodo
        // che sia avvenuto DOPO il tempo di commit della release corrente.
        for (RevCommit bugFixCommit : history.getBugFixCommits()) {
            long fixTime = bugFixCommit.getCommitTime();
            if (fixTime > releaseTime) {
                return true; // Trovato un bug-fix post-release. Il metodo era buggy.
            }
        }

        return false; // Nessun bug-fix trovato dopo la data della release.
    }



    private void estimateMissingIVs(List<JiraTicket> ticketsWithUnknownIV, double p) {
        for (JiraTicket ticket : ticketsWithUnknownIV) {
            double fv = ticket.getFixedVersion().getIndex();
            double ov = ticket.getOpeningVersion().getIndex();

            // Formula inversa: IV = FV - (FV - OV) * p
            int estimatedIvIndex = (int) Math.round(fv - (fv - ov) * p);

            // Assicurati che l'indice sia valido
            if (estimatedIvIndex < 0) estimatedIvIndex = 0;
            if (estimatedIvIndex >= releases.size()) estimatedIvIndex = releases.size() - 1;

            ticket.setInjectedVersion(releases.get(estimatedIvIndex));
        }
    }
}