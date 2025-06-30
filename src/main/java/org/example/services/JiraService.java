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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class JiraService implements AutoCloseable {

    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final String JIRA_API_URL = "https://issues.apache.org/jira/rest/api/2/search";

    // Formatter per le date di JIRA (es. "2011-05-18T12:55:23.000+0000")
    private static final DateTimeFormatter JIRA_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    public JiraService() {
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
    }

    public List<JiraTicket> getTickets(String projectKey) throws IOException {
        List<JiraTicket> allTickets = new ArrayList<>();
        final String jqlQuery = String.format(
                "project = %s AND issueType = Bug AND status in (Resolved, Closed) AND resolution = Fixed",
                projectKey
        );
        String encodedJql = URLEncoder.encode(jqlQuery, StandardCharsets.UTF_8.toString());

        int startAt = 0;
        int maxResults = 100;
        boolean hasMoreResults = true;

        System.out.println("Recupero ticket da JIRA...");

        while (hasMoreResults) {
            String url = String.format("%s?jql=%s&fields=key,resolutiondate,created&startAt=%d&maxResults=%d", JIRA_API_URL, encodedJql, startAt, maxResults);
            HttpGet request = new HttpGet(url);

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String jsonResponse = EntityUtils.toString(response.getEntity());
                JsonNode rootNode = objectMapper.readTree(jsonResponse);

                if (response.getStatusLine().getStatusCode() != 200) {
                    System.err.println("Errore da JIRA: " + response.getStatusLine().getReasonPhrase() + " - " + jsonResponse);
                    break;
                }

                JsonNode issuesNode = rootNode.path("issues");
                if (issuesNode.isEmpty()) {
                    hasMoreResults = false;
                    continue;
                }

                for (JsonNode issueNode : issuesNode) {
                    String key = issueNode.path("key").asText();
                    JsonNode fields = issueNode.path("fields");

                    // Usa ZonedDateTime con il formatter per gestire il fuso orario, poi converti a LocalDateTime
                    ZonedDateTime resolutionZoned = ZonedDateTime.parse(fields.path("resolutiondate").asText(), JIRA_DATE_FORMATTER);
                    ZonedDateTime creationZoned = ZonedDateTime.parse(fields.path("created").asText(), JIRA_DATE_FORMATTER);

                    allTickets.add(new JiraTicket(key, creationZoned.toLocalDateTime(), resolutionZoned.toLocalDateTime()));
                }

                startAt += issuesNode.size();
                if (startAt >= rootNode.path("total").asInt()) {
                    hasMoreResults = false;
                }
            }
        }
        System.out.println("Recuperati " + allTickets.size() + " ticket di bug fixati da JIRA.");
        return allTickets;
    }

    @Override
    public void close() throws IOException {
        if (this.httpClient != null) {
            this.httpClient.close();
        }
    }
}