package com.yolt.providers.web.authenticationmeans;

import com.yolt.providers.web.configuration.IntegrationTestContext;
import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTestContext
@RequiredArgsConstructor
class ListOnboardedProvidersControllerTest {

    @Autowired
    private ClientGroupRedirectUrlClientConfigurationRepository clientGroupRedirectUrlClientConfigurationRepository;
    @Autowired
    private ClientRedirectUrlClientConfigurationRepository clientRedirectUrlClientConfigurationRepository;
    @Autowired
    private ClientAuthenticationMeansRepository clientAuthenticationMeansRepository;
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void testListOnboardedBanks() {
        // setup
        final UUID clientGroupId = UUID.randomUUID();
        final UUID clientGroupRedirectUrlId = UUID.randomUUID();
        clientGroupRedirectUrlClientConfigurationRepository.upsert(new InternalClientGroupRedirectUrlClientConfiguration(
                clientGroupId, clientGroupRedirectUrlId, ServiceType.AIS, "test1", "", Instant.now()
        ));

        final UUID clientId = UUID.randomUUID();
        final UUID clientRedirectUrlId = UUID.randomUUID();
        clientRedirectUrlClientConfigurationRepository.upsert(new InternalClientRedirectUrlClientConfiguration(
                clientId, clientRedirectUrlId, ServiceType.PIS, "test2", "", Instant.now()
        ));

        final UUID clientId2 = UUID.randomUUID();
        clientAuthenticationMeansRepository.save(new InternalClientAuthenticationMeans(
                clientId2, "BUDGET_INSIGHT", "", Instant.now()
        ));

        // call
        var resp = restTemplate.exchange("/all-onboarded-providers", HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<List<ListOnboardedProvidersController.OnboardedProvider>>() {
        });

        // check
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsOnlyOnceElementsOf(List.of(
                new ListOnboardedProvidersController.OnboardedProvider(clientGroupId, null, "test1", ServiceType.AIS, clientGroupRedirectUrlId),
                new ListOnboardedProvidersController.OnboardedProvider(null, clientId, "test2", ServiceType.PIS, clientRedirectUrlId),
                new ListOnboardedProvidersController.OnboardedProvider(null, clientId2, "BUDGET_INSIGHT", ServiceType.AIS, null)
        ));
    }

}