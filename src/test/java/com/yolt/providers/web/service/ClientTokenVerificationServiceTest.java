package com.yolt.providers.web.service;

import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.verification.ClientGroupIdVerificationService;
import nl.ing.lovebird.clienttokens.verification.ClientIdVerificationService;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ClientTokenVerificationServiceTest {

    @InjectMocks
    private ClientTokenVerificationService subject;

    @Mock
    private ClientIdVerificationService clientIdVerificationService;

    @Mock
    private ClientGroupIdVerificationService clientGroupIdVerificationService;

    @Test
    void shouldCallClientIdVerificationServiceWhenClientIdIsProvidedInAuthenticationMeansReference() {
        // given
        ClientToken clientToken = new ClientToken("", null);
        UUID clientId = UUID.randomUUID();
        UUID redirectUrlId = UUID.randomUUID();
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(clientId, redirectUrlId);

        // when
        subject.verify(clientToken, authenticationMeansReference);

        // then
        then(clientIdVerificationService)
                .should()
                .verify(clientToken, clientId);
        then(clientGroupIdVerificationService)
                .shouldHaveNoInteractions();
    }

    @Test
    void shouldCallClientGroupIdVerificationServiceWhenClientIdIsProvidedInAuthenticationMeansReference() {
        // given
        ClientToken clientToken = new ClientToken("", null);
        UUID redirectUrlId = UUID.randomUUID();
        UUID clientGroupId = UUID.randomUUID();
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(null, clientGroupId, redirectUrlId);

        // when
        subject.verify(clientToken, authenticationMeansReference);

        // then
        then(clientGroupIdVerificationService)
                .should()
                .verify(clientToken, clientGroupId);
        then(clientIdVerificationService)
                .shouldHaveNoInteractions();
    }
}