package com.yolt.providers.web.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.yolt.providers.web.configuration.IntegrationTestContext;
import com.yolt.providers.web.service.dto.IngestionAccountDTO;
import com.yolt.providers.web.service.dto.IngestionRequestDTO;
import nl.ing.lovebird.clienttokens.ClientUserToken;
import nl.ing.lovebird.clienttokens.test.TestClientTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTestContext
class AccountsAndTransactionsClientTest {

    @Autowired
    private AccountsAndTransactionsClient accountsAndTransactionsClient;

    @Autowired
    private TestClientTokens testClientTokens;
    @Autowired
    private WireMockServer wireMockServer;

    @Test
    public void given_anIngestionRequests_then_itShouldBeSentCorrectly() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        ClientUserToken clientUserToken = testClientTokens.createClientUserToken(UUID.randomUUID(), UUID.randomUUID(), userId);
        IngestionAccountDTO account
                = new IngestionAccountDTO(clientUserToken.getUserIdClaim(), UUID.randomUUID(), UUID.randomUUID(), "provider",
                ProviderAccountDTOMother.newValidCurrentAccountDTOBuilder().build());
        IngestionRequestDTO message = new IngestionRequestDTO(activityId, List.of(account), UUID.randomUUID(), UUID.randomUUID());
        wireMockServer.stubFor(WireMock.post(urlMatching("/accounts-and-transactions/internal/users/" + userId + "/provider-accounts"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                ));
        // When
        accountsAndTransactionsClient.postProviderAccounts(clientUserToken, message);

        // Then
        wireMockServer.verify(1, postRequestedFor(urlMatching("/accounts-and-transactions/internal/users/" + userId + "/provider-accounts")));

        // I was a little surprised, but wiremock, with jetty automagically unzipped the content..
        List<LoggedRequest> all = wireMockServer.findAll(postRequestedFor(urlMatching("/accounts-and-transactions/internal/users/" + userId + "/provider-accounts")));
        byte[] body = all.get(0).getBody();
        assertThat(new String(body)).contains(activityId.toString());
        // assert that there's no InternalRestTemplateBuilder screwing up and setting an empty user-id header that in turns screws up the VerifiedClientTokenParameterResolver on the receiving side..
        assertThat(all.get(0).getAllHeaderKeys()).doesNotContain("user-id");

    }

}