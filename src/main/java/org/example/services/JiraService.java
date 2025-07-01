// in src/main/java/org/example/services/JiraService.java
package org.example.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.example.model.JiraTicket;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class JiraService {

    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final String JIRA_API_URL = "https://issues.apache.org/jira/rest/api/2/search";

    public JiraService() {
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Recupera da JIRA la lista completa dei ticket di tipo "Bug" risolti e fixati.
     * Per ogni ticket, estrae chiave, date e affected versions.
     */
    public List<JiraTicket> getFixedBugTickets(String projectKey) throws IOException {
        List<JiraTicket> tickets = new ArrayList<>();

        // Query JQL per trovare i bug fixati
        final String jqlQuery = String.format(
                "project = %s AND issueType = Bug AND status in (Resolved, Closed) AND resolution = Fixed",
                projectKey
        );
        String encodedJql = URLEncoder.encode(jqlQuery, StandardCharsets.UTF_8.toString());

        int startAt = 0;
        final int maxResults = 100;
        boolean hasMoreResults = true;

        System.out.println("Recupero ticket di bug fixati da JIRA...");

        while (hasMoreResults) {
            // Chiediamo i campi che ci servono: key, resolutiondate, created, versions (affected versions)
            String url = String.format("%s?jql=%s&fields=key,resolutiondate,created,versions&startAt=%d&maxResults=%d",
                    JIRA_API_URL, encodedJql, startAt, maxResults);
            HttpGet request = new HttpGet(url);

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String jsonResponse = EntityUtils.toString(response.getEntity());
                JsonNode rootNode = objectMapper.readTree(jsonResponse);

                if (response.getStatusLine().getStatusCode() != 200) {
                    System.err.println("Errore da JIRA: " + response.getStatusLine().getReasonPhrase());
                    break;
                }

                JsonNode issuesNode = rootNode.path("issues");
                if (!issuesNode.isArray() || issuesNode.size() == 0) {
                    hasMoreResults = false;
                    continue;
                }

                for (JsonNode issueNode : issuesNode) {
                    String key = issueNode.path("key").asText();
                    JsonNode fields = issueNode.path("fields");

                    // Estrai e parsa le date
                    LocalDateTime creationDate = parseJiraDate(fields.path("created").asText(null));
                    LocalDateTime resolutionDate = parseJiraDate(fields.path("resolutiondate").asText(null));

                    JiraTicket ticket = new JiraTicket(key, creationDate, resolutionDate);

                    // Estrai le "Affected Versions"
                    List<String> affectedVersions = new ArrayList<>();
                    JsonNode avNode = fields.path("versions");
                    if (avNode.isArray()) {
                        for (JsonNode versionNode : avNode) {
                            affectedVersions.add(versionNode.path("name").asText());
                        }
                    }
                    ticket.setAffectedVersionsStrings(affectedVersions);
                    tickets.add(ticket);
                }

                int total = rootNode.path("total").asInt();
                if (startAt + issuesNode.size() >= total) {
                    hasMoreResults = false;
                } else {
                    startAt += issuesNode.size();
                }
            }
        }
        System.out.println("Trovati " + tickets.size() + " ticket di bug fixati su JIRA.");
        return tickets;
    }

    private LocalDateTime parseJiraDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        // Il formato della data di JIRA è "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
        return LocalDateTime.parse(dateString, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public void close() throws IOException {
        if (this.httpClient != null) {
            this.httpClient.close();
        }
    }
}