package com.iitsaii.printagent.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iitsaii.printagent.config.PrintAgentConfig;
import com.iitsaii.printagent.dto.PrintQueueResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class BackendClient {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PrintQueueResponse getQueue() throws Exception {

        String url = PrintAgentConfig.BASE_URL + "/api/print/queue";
        System.out.println("[BACKEND] 요청 URL = " + url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 204) {
            return null;
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("Queue API Error (" + response.statusCode() + "): " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());

        return objectMapper.treeToValue(root.get("data"), PrintQueueResponse.class);
    }

    public void completePrint(String sessionId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PrintAgentConfig.BASE_URL + "/api/sessions/" + sessionId + "/print/done"))
                .method("PATCH", HttpRequest.BodyPublishers.noBody())
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }
}
