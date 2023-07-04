package com.yolt.providers.web.service.consenttesting;

import com.yolt.providers.common.domain.consenttesting.ConsentValidityRules;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.web.service.domain.ConsentTestingMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class ConsentPageValidator {

    private static final String USER_AGENT_VALUE = "Mozilla/5.0 (Linux; Android 7.0; SM-G930VC Build/NRD90M; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/58.0.3029.83 Mobile Safari/537.36";

    public ConsentTestingMessage retrieveAndValidateConsentPage(String consentPageUrl, RestTemplate restTemplate, Provider registeredProviderBean) {
        try {
            String url = URLDecoder.decode(consentPageUrl, StandardCharsets.UTF_8);
            ResponseEntity<String> response = getConsentPage(restTemplate, url);

            if (response.getStatusCode().is2xxSuccessful()) {
                return processResponse(response.getBody(), registeredProviderBean);
            }

            return ConsentTestingMessage.GENERATED;
        } catch (Exception e) {
            log.warn("CONSENT TESTING - An exception occurred during consent page retrieval {}", e.getClass().getName());
            return ConsentTestingMessage.GENERATED;
        }
    }

    private ResponseEntity<String> getConsentPage(RestTemplate restTemplate, String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.USER_AGENT, USER_AGENT_VALUE);
        log.info("About to call consent page url: {} ", url);
        try {
            return restTemplate.exchange(url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
        } catch (HttpStatusCodeException e) {
            log.info("Received status: {} calling url: {}", e.getStatusCode(), url);
            throw e;
        }
    }

    private ConsentTestingMessage processResponse(String htmlBody, Provider registeredProviderBean) {
        if (registeredProviderBean.getConsentValidityRules().equals(ConsentValidityRules.EMPTY_RULES_SET)) {
            return ConsentTestingMessage.STATUS_200_EMPTY_VALIDITY_RULES;
        }

        for (String keyword : registeredProviderBean.getConsentValidityRules().getKeywords()) {
            if (!htmlBody.contains(keyword)) {
                log.warn("CONSENT TESTING - Keyword was not found on {} consent page",
                        registeredProviderBean.getProviderIdentifier());
                return ConsentTestingMessage.STATUS_200;
            }
        }

        return ConsentTestingMessage.VALIDITY_RULES_CHECKED;
    }
}
