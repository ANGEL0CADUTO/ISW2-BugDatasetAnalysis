// in src/main/java/org/example/logic/BugginessLogic.java
package org.example.logic;

import org.eclipse.jgit.revwalk.RevCommit;
import org.example.model.JiraTicket;
import org.example.model.MethodHistory;
import org.example.model.Release;
import java.time.ZonedDateTime; // <-- IMPORT CAMBIATO
import java.util.*;
import java.util.stream.Collectors;

public class BugginessLogic {

    private final List<Release> releases;
    private final Map<String, MethodHistory> methodsHistories;

    private final List<Double> pValues = new ArrayList<>();
    private static final int COLD_START_THRESHOLD = 5;


    public BugginessLogic(List<Release> releases, Map<String, MethodHistory> methodsHistories) {
        this.releases = releases;
        this.methodsHistories = methodsHistories;
    }

    public void calculateBugLifecycles(List<JiraTicket> tickets) {
        System.out.println("Avvio calcolo del ciclo di vita dei bug (IV, OV, FV) con Proportion...");

        // --- CONTATORI DI DEBUG ---
        int totalTickets = tickets.size();
        int withKnownIv = 0;
        int validForProportion = 0;
        int toEstimate = 0;
        int unusableOvFvMissing = 0;
        // -------------------------

        // Popola la lista pValues e conta i ticket
        for (JiraTicket ticket : tickets) {
            setInitialVersions(ticket);

            if (ticket.getInjectedVersion() != null) {
                withKnownIv++;
                // Controlla se è valido per il calcolo di p
                if (ticket.getOpeningVersion() != null && ticket.getFixedVersion() != null &&
                        ticket.getInjectedVersion().getIndex() <= ticket.getOpeningVersion().getIndex() &&
                        ticket.getOpeningVersion().getIndex() < ticket.getFixedVersion().getIndex()) {

                    validForProportion++;
                    double fvIndex = ticket.getFixedVersion().getIndex();
                    double ivIndex = ticket.getInjectedVersion().getIndex();
                    double ovIndex = ticket.getOpeningVersion().getIndex();

                    if (fvIndex != ovIndex) {
                        double p = (fvIndex - ivIndex) / (fvIndex - ovIndex);
                        if (!Double.isInfinite(p) && !Double.isNaN(p) && p >= 0) {
                            pValues.add(p);
                        }
                    }
                }
            } else { // IV è null, quindi potenzialmente da stimare
                if (ticket.getOpeningVersion() != null && ticket.getFixedVersion() != null) {
                    // Controlla anche qui la coerenza di OV e FV
                    if (ticket.getOpeningVersion().getIndex() < ticket.getFixedVersion().getIndex()) {
                        toEstimate++;
                    } else {
                        // OV >= FV, quindi inutilizzabile
                        unusableOvFvMissing++;
                    }
                } else {
                    // OV o FV mancanti, quindi inutilizzabile
                    unusableOvFvMissing++;
                }
            }
        }
        Collections.sort(pValues);

        // --- REPORT DI DEBUG ---
        System.out.println("\n--- REPORT DIAGNOSTICO CICLO DI VITA ---");
        System.out.println("Ticket totali analizzati: " + totalTickets);
        System.out.println("------------------------------------------");
        System.out.println("Ticket con 'Affected Version' (IV nota): " + withKnownIv);
        System.out.println("  -> Di cui con dati validi (IV<=OV<FV) per Proportion: " + validForProportion);
        System.out.println("------------------------------------------");
        System.out.println("Ticket senza 'Affected Version' (IV da stimare): " + (totalTickets - withKnownIv));
        System.out.println("  -> Di cui stimabili (con OV e FV validi): " + toEstimate);
        System.out.println("  -> Di cui inutilizzabili (OV/FV mancanti o incoerenti): " + unusableOvFvMissing);
        System.out.println("------------------------------------------");
        System.out.println("Verifica: " + withKnownIv + " (con IV) + " + toEstimate + " (stimabili) + " + unusableOvFvMissing + " (inutilizzabili) = " + (withKnownIv + toEstimate + unusableOvFvMissing));
        System.out.println("------------------------------------------\n");


        // Ora stima le IV mancanti
        List<JiraTicket> ticketsToEstimateList = tickets.stream()
                .filter(t -> t.getInjectedVersion() == null && t.getOpeningVersion() != null && t.getFixedVersion() != null
                        && t.getOpeningVersion().getIndex() < t.getFixedVersion().getIndex())
                .collect(Collectors.toList());

        System.out.println("Stimando " + ticketsToEstimateList.size() + " IV mancanti...");
        estimateMissingIVs(ticketsToEstimateList);
    }

    private double getProportionValue() {
        if (pValues.size() < COLD_START_THRESHOLD) {
            // COLD START: Usiamo un valore di default robusto. La mediana osservata è 1.0.
            return 1.0;
        } else {
            // INCREMENT: Usiamo la mediana dei valori raccolti per robustezza.
            int middle = pValues.size() / 2;
            if (pValues.size() % 2 == 1) {
                return pValues.get(middle);
            } else {
                return (pValues.get(middle - 1) + pValues.get(middle)) / 2.0;
            }
        }
    }
    private void estimateMissingIVs(List<JiraTicket> ticketsWithUnknownIV) {
        double p = getProportionValue();
        System.out.println("Valore di Proportion (p) utilizzato per la stima: " + p);

        for (JiraTicket ticket : ticketsWithUnknownIV) {
            double fv = ticket.getFixedVersion().getIndex();
            double ov = ticket.getOpeningVersion().getIndex();
            // Assicura che la stima non vada fuori range
            if (fv <= ov) continue;

            int estimatedIvIndex = (int) Math.round(fv - (fv - ov) * p);
            if (estimatedIvIndex < 0) estimatedIvIndex = 0;
            // La IV stimata non può essere successiva alla OV
            if (estimatedIvIndex > ov) estimatedIvIndex = (int)ov;

            ticket.setInjectedVersion(releases.get(Math.min(estimatedIvIndex, releases.size() - 1)));
        }
    }

    private boolean setInitialVersions(JiraTicket ticket) {
        // --- CHIAMATA CORRETTA PER OV ---
        ticket.setOpeningVersion(findReleaseByCreationDate(ticket.getCreationDate()));
        // La logica per FV rimane la stessa
        ticket.setFixedVersion(findReleaseByFixDate(ticket.getResolutionDate()));
        setInjectedVersionFromAffected(ticket);
        return ticket.getOpeningVersion() != null && ticket.getFixedVersion() != null;
    }
    private Release findReleaseByCreationDate(ZonedDateTime creationDate) {
        if (creationDate == null) return null;
        return releases.stream()
                .filter(r -> !r.getDate().isAfter(creationDate)) // Data della release è prima o uguale alla data di creazione
                .max(Comparator.comparing(Release::getDate)) // Trova la più recente tra queste
                .orElse(null);
    }

    // Rinominiamo il vecchio metodo per chiarezza, la logica è la stessa
    private Release findReleaseByFixDate(ZonedDateTime fixDate) {
        if (fixDate == null) return null;
        return releases.stream()
                .filter(r -> r.getDate().isAfter(fixDate)) // Data della release è dopo la data di fix
                .min(Comparator.comparing(Release::getDate)) // Trova la più vicina (la prima dopo)
                .orElse(null);
    }



    private void setInjectedVersionFromAffected(JiraTicket ticket) {
        ticket.getAffectedVersionsStrings().stream()
                .map(this::findReleaseByName)
                .filter(Objects::nonNull)
                .min(Comparator.comparing(Release::getDate))
                .ifPresent(ticket::setInjectedVersion);
    }

    private Release findReleaseByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (Release r : this.releases) {
            // Normalizza il nome della release da Git rimuovendo il prefisso comune "release-"
            String releaseNameFromGit = r.getName();
            String normalizedGitName = releaseNameFromGit.startsWith("release-")
                    ? releaseNameFromGit.substring(8)
                    : releaseNameFromGit;

            // Confronta il nome normalizzato con quello di JIRA (ignorando maiuscole/minuscole per sicurezza)
            if (normalizedGitName.equalsIgnoreCase(name)) {
                return r;
            }
        }

        return null;
    }

// in BugginessLogic.java

    private double calculateProportion(List<JiraTicket> ticketsWithKnownIV) {
        if (ticketsWithKnownIV.isEmpty()) return 0.5;

        System.out.println("\n--- INIZIO DEBUG AVANZATO PROPORTION ---");
        List<Double> pValues = new ArrayList<>();

        for (JiraTicket t : ticketsWithKnownIV) {
            boolean versionsExist = t.getInjectedVersion() != null && t.getOpeningVersion() != null && t.getFixedVersion() != null;
            if (!versionsExist) continue;

            boolean logicalOrder = t.getInjectedVersion().getIndex() <= t.getOpeningVersion().getIndex()
                    && t.getOpeningVersion().getIndex() < t.getFixedVersion().getIndex();

            if (!logicalOrder) continue; // Salta i ticket con ordine illogico che abbiamo già identificato

            double fvIndex = t.getFixedVersion().getIndex();
            double ivIndex = t.getInjectedVersion().getIndex();
            double ovIndex = t.getOpeningVersion().getIndex();

            // Evita divisione per zero
            if (fvIndex == ovIndex) continue;

            double p = (fvIndex - ivIndex) / (fvIndex - ovIndex);

            // Filtra valori insensati
            if (Double.isInfinite(p) || Double.isNaN(p) || p < 0) continue;

            // --- STAMPA DI DEBUG PER OGNI VALORE DI P ---
            System.out.printf("Ticket: %-12s | p = %.4f | IV: %-15s (idx:%3.0f) | OV: %-15s (idx:%3.0f) | FV: %-15s (idx:%3.0f)%n",
                    t.getKey(), p,
                    t.getInjectedVersion().getName(), ivIndex,
                    t.getOpeningVersion().getName(), ovIndex,
                    t.getFixedVersion().getName(), fvIndex);
            // ---------------------------------------------

            pValues.add(p);
        }

        System.out.println("--- FINE DEBUG AVANZATO ---");

        if (pValues.isEmpty()) {
            System.out.println("[WARNING] Nessun ticket con dati validi per calcolare Proportion. Si usa il valore di default 0.5.");
            return 0.5;
        }

        double averageP = pValues.stream().mapToDouble(d -> d).average().orElse(0.5);
        System.out.println("Media di p calcolata su " + pValues.size() + " valori: " + averageP);

        // Per ora, terminiamo per analizzare i dati
        System.exit(1);

        return averageP;
    }



    public boolean isBuggy(String methodID, Release currentRelease, List<JiraTicket> tickets) {
        for (JiraTicket ticket : tickets) {
            if (ticket.getInjectedVersion() == null || ticket.getFixedVersion() == null) continue;

            // Controlla se il metodo è stato toccato da un commit associato a questo ticket
            MethodHistory history = methodsHistories.get(methodID);
            if (history == null) continue;

            boolean ticketTouchesMethod = history.getBugFixCommits().stream()
                    .anyMatch(commit -> commit.getFullMessage().contains(ticket.getKey()));

            if (ticketTouchesMethod) {
                int ivIndex = ticket.getInjectedVersion().getIndex();
                int fvIndex = ticket.getFixedVersion().getIndex();
                int currentIndex = currentRelease.getIndex();

                // Regola: IV <= v < FV
                if (currentIndex >= ivIndex && currentIndex < fvIndex) {
                    return true;
                }
            }
        }
        return false;
    }
}