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
    // ... (il resto della classe è simile, ma le firme dei metodi usano ZonedDateTime) ...
    private final List<Release> releases;
    private final Map<String, MethodHistory> methodsHistories;

    public BugginessLogic(List<Release> releases, Map<String, MethodHistory> methodsHistories) {
        this.releases = releases;
        this.methodsHistories = methodsHistories;
    }

    public void calculateBugLifecycles(List<JiraTicket> tickets) {
        System.out.println("Avvio calcolo del ciclo di vita dei bug (IV, OV, FV) con Proportion...");
        List<JiraTicket> withKnownIV = tickets.stream().filter(t -> setInitialVersions(t) && t.getInjectedVersion() != null).collect(Collectors.toList());
        List<JiraTicket> withUnknownIV = tickets.stream().filter(t -> t.getOpeningVersion() != null && t.getFixedVersion() != null && t.getInjectedVersion() == null).collect(Collectors.toList());
        System.out.println("Trovati " + withKnownIV.size() + " ticket con IV nota e " + withUnknownIV.size() + " con IV da stimare.");
        double p = calculateProportion(withKnownIV);
        System.out.println("Valore di Proportion (p) calcolato: " + p);
        estimateMissingIVs(withUnknownIV, p);
    }

    private boolean setInitialVersions(JiraTicket ticket) {
        ticket.setOpeningVersion(findReleaseByDate(ticket.getCreationDate()));
        ticket.setFixedVersion(findReleaseByDate(ticket.getResolutionDate()));
        setInjectedVersionFromAffected(ticket);
        return ticket.getOpeningVersion() != null && ticket.getFixedVersion() != null;
    }

    private Release findReleaseByDate(ZonedDateTime date) { // <-- TIPO CAMBIATO
        if (date == null) return null;
        return releases.stream().filter(r -> r.getDate().isAfter(date)).min(Release::compareTo).orElse(null);
    }

    private void setInjectedVersionFromAffected(JiraTicket ticket) {
        ticket.getAffectedVersionsStrings().stream()
                .map(this::findReleaseByName)
                .filter(Objects::nonNull)
                .min(Comparator.comparing(Release::getDate))
                .ifPresent(ticket::setInjectedVersion);
    }

    private Release findReleaseByName(String name) {
        return releases.stream().filter(r -> r.getName().equals(name)).findFirst().orElse(null);
    }

    private double calculateProportion(List<JiraTicket> ticketsWithKnownIV) {
        if (ticketsWithKnownIV.isEmpty()) return 0.5;
        List<Double> pValues = ticketsWithKnownIV.stream()
                .map(t -> (double) (t.getFixedVersion().getIndex() - t.getInjectedVersion().getIndex()) / (t.getFixedVersion().getIndex() - t.getOpeningVersion().getIndex()))
                .filter(p -> !p.isInfinite() && !p.isNaN()).collect(Collectors.toList());
        return pValues.isEmpty() ? 0.5 : pValues.stream().mapToDouble(d -> d).average().orElse(0.5);
    }

    private void estimateMissingIVs(List<JiraTicket> ticketsWithUnknownIV, double p) {
        for (JiraTicket ticket : ticketsWithUnknownIV) {
            double fv = ticket.getFixedVersion().getIndex();
            double ov = ticket.getOpeningVersion().getIndex();
            int estimatedIvIndex = (int) Math.round(fv - (fv - ov) * p);
            if (estimatedIvIndex < 0) estimatedIvIndex = 0;
            ticket.setInjectedVersion(releases.get(Math.min(estimatedIvIndex, releases.size() - 1)));
        }
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