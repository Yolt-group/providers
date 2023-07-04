package com.yolt.providers.web.cryptography;

import nl.ing.lovebird.clienttokens.AbstractClientToken;

import javax.validation.constraints.NotNull;
import java.security.PrivateKey;
import java.util.UUID;

public interface KeyService {
    @NotNull
    PrivateKey getPrivateTransportKey(final AbstractClientToken clientToken, final UUID kid) throws KeyNotFoundException;


    PrivateKey getPrivateSigningKey(final AbstractClientToken clientToken, final UUID kid) throws KeyNotFoundException;
}
