// in src/main/java/org/example/logic/BugginessLogic.java
package org.example.logic;

import org.eclipse.jgit.revwalk.RevCommit;
import org.example.model.JiraTicket;
import org.example.model.MethodHistory;
import org.example.model.Release;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BugginessLogic {

    private static final Logger LOGGER = Logger.getLogger(BugginessLogic.class.getName());
    private static final String SEPARATOR_LINE = "------------------------------------------";

    private final List<Release> releases;
    private final Map<String, MethodHistory> methodsHistories;

    private final List<Double> pValues = new ArrayList<>();
    private static final int COLD_START_THRESHOLD = 5;

    public BugginessLogic(List<Release> releases, Map<String, MethodHistory> methodsHistories) {
        this.releases = releases;
        this.methodsHistories = methodsHistories;
    }

    /**
     * Orchestratore principale per il calcolo del ciclo di vita dei bug.
     */
    public void calculateBugLifecycles(List<JiraTicket> tickets) {
        LOGGER.info("Avvio calcolo del ciclo di vita dei bug (IV, OV, FV) con Proportion...");

        populatePValuesAndSetInitialVersions(tickets);

        List<JiraTicket> ticketsToEstimateList = tickets.stream()
                .filter(t -> t.getInjectedVersion() == null && t.getOpeningVersion() != null && t.getFixedVersion() != null
                        && t.getOpeningVersion().getIndex() < t.getFixedVersion().getIndex())
                .toList(); // Smell 4: Sostituito con .toList()

        LOGGER.log(Level.INFO, "Stimando {0} IV mancanti...", ticketsToEstimateList.size());
        estimateMissingIVs(ticketsToEstimateList);
    }

    /**
     * Itera sui ticket per calcolare i valori di 'p' e stampare un report diagnostico.
     */
    private void populatePValuesAndSetInitialVersions(List<JiraTicket> tickets) {
        int withKnownIv = 0;
        int validForProportion = 0;
        int toEstimate = 0;
        int unusable = 0;

        for (JiraTicket ticket : tickets) {
            setInitialVersions(ticket);

            if (ticket.getInjectedVersion() != null) {
                withKnownIv++;
                if (isTicketValidForProportion(ticket)) {
                    validForProportion++;
                    calculatePValue(ticket);
                }
            } else {
                if (isTicketEstimable(ticket)) {
                    toEstimate++;
                } else {
                    unusable++;
                }
            }
        }
        Collections.sort(pValues);
        logLifecycleReport(tickets.size(), withKnownIv, validForProportion, toEstimate, unusable);
    }

    private boolean isTicketValidForProportion(JiraTicket ticket) {
        return ticket.getOpeningVersion() != null && ticket.getFixedVersion() != null &&
                ticket.getInjectedVersion().getIndex() <= ticket.getOpeningVersion().getIndex() &&
                ticket.getOpeningVersion().getIndex() < ticket.getFixedVersion().getIndex();
    }

    private void calculatePValue(JiraTicket ticket) {
        double fvIndex = ticket.getFixedVersion().getIndex();
        double ivIndex = ticket.getInjectedVersion().getIndex();
        double ovIndex = ticket.getOpeningVersion().getIndex();

        if (fvIndex > ovIndex) {
            double p = (fvIndex - ivIndex) / (fvIndex - ovIndex);
            if (!Double.isInfinite(p) && !Double.isNaN(p) && p >= 0) {
                pValues.add(p);
            }
        }
    }

    private boolean isTicketEstimable(JiraTicket ticket) {
        return ticket.getOpeningVersion() != null && ticket.getFixedVersion() != null &&
                ticket.getOpeningVersion().getIndex() < ticket.getFixedVersion().getIndex();
    }

    private void logLifecycleReport(int total, int withIv, int valid, int toEstimate, int unusable) {
        LOGGER.log(Level.INFO, "%n--- REPORT DIAGNOSTICO CICLO DI VITA ---");
        LOGGER.log(Level.INFO, "Ticket totali analizzati: {0}", total);
        LOGGER.info(SEPARATOR_LINE);
        LOGGER.log(Level.INFO, "Ticket con 'Affected Version' (IV nota): {0}", withIv);
        LOGGER.log(Level.INFO, "  -> Di cui con dati validi (IV<=OV<FV) per Proportion: {0}", valid);
        LOGGER.info(SEPARATOR_LINE);
        LOGGER.log(Level.INFO, "Ticket senza 'Affected Version' (IV da stimare): {0}", (total - withIv));
        LOGGER.log(Level.INFO, "  -> Di cui stimabili (con OV e FV validi): {0}", toEstimate);
        LOGGER.log(Level.INFO, "  -> Di cui inutilizzabili (OV/FV mancanti o incoerenti): {0}", unusable);
        LOGGER.info(SEPARATOR_LINE);
        LOGGER.log(Level.INFO, "Verifica: {0} (con IV) + {1} (stimabili) + {2} (inutilizzabili) = {3}",
                new Object[]{withIv, toEstimate, unusable, (withIv + toEstimate + unusable)});
        LOGGER.log(Level.INFO, "%n" + SEPARATOR_LINE + "%n");
    }

    private double getProportionValue() {
        if (pValues.size() < COLD_START_THRESHOLD) {
            return 1.0;
        }
        // Usa la mediana per robustezza agli outlier
        int middle = pValues.size() / 2;
        if (pValues.size() % 2 == 1) {
            return pValues.get(middle);
        } else {
            return (pValues.get(middle - 1) + pValues.get(middle)) / 2.0;
        }
    }

    private void estimateMissingIVs(List<JiraTicket> ticketsWithUnknownIV) {
        double p = getProportionValue();
        LOGGER.log(Level.INFO, "Valore di Proportion (p) utilizzato per la stima: {0}", p);

        for (JiraTicket ticket : ticketsWithUnknownIV) {
            double fv = ticket.getFixedVersion().getIndex();
            double ov = ticket.getOpeningVersion().getIndex();

            int estimatedIvIndex = (int) Math.round(fv - (fv - ov) * p);

            // Applica vincoli per mantenere la stima sensata
            if (estimatedIvIndex < 0) estimatedIvIndex = 0;
            if (estimatedIvIndex > ov) estimatedIvIndex = (int) ov;

            ticket.setInjectedVersion(releases.get(Math.min(estimatedIvIndex, releases.size() - 1)));
        }
    }

    private void setInitialVersions(JiraTicket ticket) {
        ticket.setOpeningVersion(findReleaseByCreationDate(ticket.getCreationDate()));
        ticket.setFixedVersion(findReleaseByFixDate(ticket.getResolutionDate()));
        setInjectedVersionFromAffected(ticket);
    }

    private Release findReleaseByCreationDate(ZonedDateTime creationDate) {
        if (creationDate == null) return null;
        return releases.stream()
                .filter(r -> !r.getDate().isAfter(creationDate))
                .max(Comparator.comparing(Release::getDate))
                .orElse(null);
    }

    private Release findReleaseByFixDate(ZonedDateTime fixDate) {
        if (fixDate == null) return null;
        return releases.stream()
                .filter(r -> r.getDate().isAfter(fixDate))
                .min(Comparator.comparing(Release::getDate))
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
        if (name == null || name.isEmpty()) return null;

        return releases.stream()
                .filter(r -> {
                    String releaseNameFromGit = r.getName();
                    String normalizedGitName = releaseNameFromGit.startsWith("release-")
                            ? releaseNameFromGit.substring(8)
                            : releaseNameFromGit;
                    return normalizedGitName.equalsIgnoreCase(name);
                })
                .findFirst()
                .orElse(null);
    }

    public boolean isBuggy(String methodID, Release currentRelease, List<JiraTicket> tickets) {
        MethodHistory history = methodsHistories.get(methodID);
        if (history == null) return false;

        for (JiraTicket ticket : tickets) {
            if (ticket.getInjectedVersion() == null || ticket.getFixedVersion() == null) continue;

            boolean ticketTouchesMethod = history.getBugFixCommits().stream()
                    .anyMatch(commit -> commit.getFullMessage().contains(ticket.getKey()));

            if (ticketTouchesMethod) {
                int ivIndex = ticket.getInjectedVersion().getIndex();
                int fvIndex = ticket.getFixedVersion().getIndex();
                int currentIndex = currentRelease.getIndex();

                if (currentIndex >= ivIndex && currentIndex < fvIndex) {
                    return true;
                }
            }
        }
        return false;
    }
}