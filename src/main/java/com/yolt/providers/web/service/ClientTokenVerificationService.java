package com.yolt.providers.web.service;

import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.verification.ClientGroupIdVerificationService;
import nl.ing.lovebird.clienttokens.verification.ClientIdVerificationService;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientTokenVerificationService {

    private final ClientIdVerificationService clientIdVerificationService;
    private final ClientGroupIdVerificationService clientGroupIdVerificationService;

    public void verify(final ClientToken clientToken, final AuthenticationMeansReference authenticationMeansReference) {
        UUID clientId = authenticationMeansReference.getClientId();
        if (clientId != null) {
            clientIdVerificationService.verify(clientToken, clientId);
        } else {
            clientGroupIdVerificationService.verify(clientToken, authenticationMeansReference.getClientGroupId());
        }
    }
}
