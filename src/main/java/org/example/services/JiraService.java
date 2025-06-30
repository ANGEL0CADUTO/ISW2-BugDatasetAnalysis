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

public class JiraService implements AutoCloseable {

    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final String JIRA_API_URL = "https://issues.apache.org/jira/rest/api/2/search";

    public JiraService() {
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Recupera tutti i ticket di bug fixati per un dato progetto.
     * @param projectKey La chiave del progetto (es. "BOOKKEEPER").
     * @return Una lista di oggetti JiraTicket.
     */
    public List<JiraTicket> getTickets(String projectKey) throws IOException {
        List<JiraTicket> allTickets = new ArrayList<>();

        // Query per i bug fixati, richiedendo i campi che ci servono
        final String jqlQuery = String.format(
                "project = %s AND issueType = Bug AND status in (Resolved, Closed) AND resolution = Fixed ORDER BY resolutiondate ASC",
                projectKey
        );
        String encodedJql = URLEncoder.encode(jqlQuery, StandardCharsets.UTF_8.toString());

        int startAt = 0;
        int maxResults = 100;
        boolean hasMoreResults = true;

        System.out.println("Recupero ticket da JIRA...");

        while (hasMoreResults) {
            // Chiediamo a JIRA i campi 'versions' (affected versions), 'created', 'resolutiondate'
            String url = String.format("%s?jql=%s&fields=versions,created,resolutiondate&startAt=%d&maxResults=%d",
                    JIRA_API_URL, encodedJql, startAt, maxResults);
            HttpGet request = new HttpGet(url);

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String jsonResponse = EntityUtils.toString(response.getEntity());
                JsonNode rootNode = objectMapper.readTree(jsonResponse);

                if (response.getStatusLine().getStatusCode() != 200) {
                    System.err.println("Errore da JIRA: " + response.getStatusLine().getReasonPhrase() + " - " + jsonResponse);
                    break;
                }

                JsonNode issuesNode = rootNode.path("issues");
                if (!issuesNode.isArray() || issuesNode.isEmpty()) {
                    hasMoreResults = false;
                    continue;
                }

                for (JsonNode issueNode : issuesNode) {
                    JiraTicket ticket = parseTicket(issueNode);
                    allTickets.add(ticket);
                }

                int total = rootNode.path("total").asInt();
                if (startAt + issuesNode.size() >= total) {
                    hasMoreResults = false;
                } else {
                    startAt += issuesNode.size();
                }
            }
        }
        System.out.println("Recuperati " + allTickets.size() + " ticket di bug fixati da JIRA.");
        return allTickets;
    }

    /**
     * Metodo helper per parsare un singolo ticket JSON in un oggetto JiraTicket.
     */
    private JiraTicket parseTicket(JsonNode issueNode) {
        String key = issueNode.path("key").asText();
        JsonNode fields = issueNode.path("fields");

        // Le date di JIRA sono in formato ISO_OFFSET_DATE_TIME, es. "2023-01-15T10:00:00.000+0100"
        LocalDateTime creationDate = LocalDateTime.parse(fields.path("created").asText(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        LocalDateTime resolutionDate = LocalDateTime.parse(fields.path("resolutiondate").asText(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        JiraTicket ticket = new JiraTicket(key, creationDate, resolutionDate);

        // Estrai le "Affected Versions"
        JsonNode affectedVersionsNode = fields.path("versions");
        if (affectedVersionsNode.isArray()) {
            for (JsonNode versionNode : affectedVersionsNode) {
                ticket.addAffectedVersion(versionNode.path("name").asText());
            }
        }
        return ticket;
    }

    @Override
    public void close() throws IOException {
        if (this.httpClient != null) {
            this.httpClient.close();
        }
    }
}