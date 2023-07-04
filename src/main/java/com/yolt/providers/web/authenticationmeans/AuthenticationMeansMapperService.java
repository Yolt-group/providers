package com.yolt.providers.web.authenticationmeans;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthenticationMeansMapperService {

    private final Clock clock;
    private final AuthenticationMeansEncryptionService authenticationMeansEncryptionService;

    public InternalClientRedirectUrlClientConfiguration mapToInternal(ClientRedirectUrlProviderClientConfiguration clientRedirectUrlProviderClientConfiguration) {
        String encryptedAuthenticationMeans = authenticationMeansEncryptionService.encryptAuthenticationMeans(clientRedirectUrlProviderClientConfiguration.getAuthenticationMeans());
        return new InternalClientRedirectUrlClientConfiguration(
                clientRedirectUrlProviderClientConfiguration.getClientId(),
                clientRedirectUrlProviderClientConfiguration.getRedirectUrlId(),
                clientRedirectUrlProviderClientConfiguration.getServiceType(),
                clientRedirectUrlProviderClientConfiguration.getProvider(),
                encryptedAuthenticationMeans,
                Instant.now(clock)
        );
    }

    public InternalClientAuthenticationMeans mapToInternal(ClientProviderAuthenticationMeans clientProviderAuthenticationMeans) {
        String encryptedAuthenticationMeans = authenticationMeansEncryptionService.encryptAuthenticationMeans(clientProviderAuthenticationMeans.getAuthenticationMeans());
        return new InternalClientAuthenticationMeans(
                clientProviderAuthenticationMeans.getClientId(),
                clientProviderAuthenticationMeans.getProvider(),
                encryptedAuthenticationMeans,
                Instant.now(clock)
        );
    }

    public InternalClientGroupRedirectUrlClientConfiguration mapToInternal(ClientGroupRedirectUrlProviderClientConfiguration clientGroupRedirectUrlProviderClientConfiguration) {
        String encryptedAuthenticationMeans = authenticationMeansEncryptionService.encryptAuthenticationMeans(clientGroupRedirectUrlProviderClientConfiguration.getAuthenticationMeans());
        return new InternalClientGroupRedirectUrlClientConfiguration(
                clientGroupRedirectUrlProviderClientConfiguration.getClientGroupId(),
                clientGroupRedirectUrlProviderClientConfiguration.getRedirectUrlId(),
                clientGroupRedirectUrlProviderClientConfiguration.getServiceType(),
                clientGroupRedirectUrlProviderClientConfiguration.getProvider(),
                encryptedAuthenticationMeans,
                Instant.now(clock)
        );

    }
}
