package com.yolt.providers.web.authenticationmeans.startuplogging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties(prefix = "authmeanlogging")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationMeansLoggingProperties {

    private Map<String, List<AuthMeansPropertyReference>> providerReferences = new HashMap<>();

    public Set<String> getProviderIds() {
        return providerReferences.keySet();
    }

    List<AuthMeansPropertyReference> getProviderReferences(String providerId) {
        return providerReferences.get(providerId);
    }

    Set<String> getProviderIdsContainingDetails() {
        return providerReferences.keySet().stream().filter(key -> !providerReferences.get(key).isEmpty()).collect(Collectors.toSet());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AuthMeansPropertyReference {

        private UUID clientId;
        private UUID clientGroupId;
        private UUID redirectUrlId;
    }
}