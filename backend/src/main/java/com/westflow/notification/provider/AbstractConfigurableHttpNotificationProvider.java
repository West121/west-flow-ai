package com.westflow.notification.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.common.error.ContractException;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.notification.model.NotificationSendResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpStatus;

abstract class AbstractConfigurableHttpNotificationProvider implements NotificationProvider {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    protected AbstractConfigurableHttpNotificationProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public NotificationSendResult send(NotificationChannelRecord channel, NotificationDispatchRequest request) {
        Map<String, Object> config = channel.config() == null ? Map.of() : channel.config();
        if (allowDiagnosticMock(channel, config)) {
            return new NotificationSendResult(
                    true,
                    mockProviderName(),
                    String.valueOf(config.getOrDefault("mockResponseMessage", mockSuccessMessage()))
            );
        }

        String endpoint = requireString(config, endpointField(), validationMessage(), true);
        String accessToken = requireString(config, "accessToken", validationMessage(), false);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(buildRequestBody(channel, request, config)),
                            StandardCharsets.UTF_8
                    ));

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new NotificationSendResult(
                        true,
                        successProviderName(),
                        response.body() == null || response.body().isBlank() ? successMessage() : response.body()
                );
            }
            throw new ContractException(
                    errorCode(),
                    HttpStatus.BAD_GATEWAY,
                    failureMessage() + "，状态码=" + response.statusCode() + "，响应=" + response.body(),
                    Map.of("endpoint", endpoint, "statusCode", response.statusCode(), "responseBody", response.body())
            );
        } catch (Exception exception) {
            if (exception instanceof ContractException contractException) {
                throw contractException;
            }
            throw new ContractException(
                    errorCode(),
                    HttpStatus.BAD_GATEWAY,
                    failureMessage() + "，错误=" + exception.getMessage(),
                    Map.of("endpoint", endpoint, "error", exception.getMessage())
            );
        }
    }

    protected String requireString(Map<String, Object> config, String key, String message, boolean allowAlternateUrl) {
        Object value = config.get(key);
        if (allowAlternateUrl && (value == null || String.valueOf(value).isBlank()) && "endpoint".equals(key)) {
            value = config.get("url");
        }
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    message,
                    Map.of("field", key)
            );
        }
        return String.valueOf(value);
    }

    protected String endpointField() {
        return "endpoint";
    }

    private boolean allowDiagnosticMock(NotificationChannelRecord channel, Map<String, Object> config) {
        if (!Boolean.TRUE.equals(channel.mockMode())) {
            return false;
        }
        Object endpointValue = config.getOrDefault("endpoint", config.get("url"));
        if (endpointValue == null) {
            return false;
        }
        String endpoint = String.valueOf(endpointValue).trim();
        return endpoint.startsWith("http://127.0.0.1")
                || endpoint.startsWith("http://localhost")
                || endpoint.startsWith("https://127.0.0.1")
                || endpoint.startsWith("https://localhost");
    }

    protected abstract Map<String, Object> buildRequestBody(
            NotificationChannelRecord channel,
            NotificationDispatchRequest request,
            Map<String, Object> config
    );

    protected abstract String validationMessage();

    protected abstract String errorCode();

    protected abstract String failureMessage();

    protected abstract String successMessage();

    protected abstract String successProviderName();

    protected abstract String mockProviderName();

    protected abstract String mockSuccessMessage();
}
