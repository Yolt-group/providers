package com.yolt.providers.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.web.rest.InternalRestTemplateBuilder;
import com.yolt.providers.web.service.dto.IngestionRequestDTO;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientUserToken;
import nl.ing.lovebird.clienttokens.constants.ClientTokenConstants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import static com.yolt.providers.web.configuration.ApplicationConfiguration.OBJECT_MAPPER;

@Service
@Slf4j
public class AccountsAndTransactionsClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AccountsAndTransactionsClient(
            final InternalRestTemplateBuilder internalRestTemplateBuilder,
            @Value("${service.accountsAndTransactions.url}") String baseUrl,
            @Qualifier(OBJECT_MAPPER) final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restTemplate = internalRestTemplateBuilder
                .rootUri(baseUrl)
                .build();
    }

    public void postProviderAccounts(@NonNull ClientUserToken clientUserToken, IngestionRequestDTO ingestionRequestDTO) {
        var userIdString = clientUserToken.getUserIdClaim();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME, clientUserToken.getSerialized());
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(baos)) {
            objectMapper.writeValue(gzipOutputStream, ingestionRequestDTO);

            HttpEntity<byte[]> request = new HttpEntity<>(baos.toByteArray(), headers);

            ResponseEntity<String> exchange = restTemplate.exchange("/internal/users/{userId}/provider-accounts", HttpMethod.POST, request, String.class, userIdString);

            if (!exchange.getStatusCode().is2xxSuccessful()) {
                log.error("something went wrong sending ingestionRequestDTO over http! status: {}", exchange.getStatusCode());

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
