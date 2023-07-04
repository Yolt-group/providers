package com.yolt.providers.web.cryptography.signing;

import com.yolt.providers.web.cryptography.KeyService;
import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.clienttokens.ClientGroupToken;
import nl.ing.lovebird.clienttokens.ClientToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JcaSignerFactory {

    private final KeyService keyservice;

    public JcaSigner getForClientToken(ClientToken clientToken) {
        return new JcaSigner(clientToken, keyservice);
    }

    public JcaSigner getForClientGroupToken(ClientGroupToken clientGroupToken) {
        return new JcaSigner(clientGroupToken, keyservice);
    }
}
