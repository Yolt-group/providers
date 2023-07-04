package com.yolt.providers.web.cryptography.signing;

import com.yolt.providers.common.cryptography.Signer;
import com.yolt.providers.web.cryptography.KeyNotFoundException;
import com.yolt.providers.web.cryptography.KeyService;
import com.yolt.securityutils.signing.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.AbstractClientToken;
import nl.ing.lovebird.clienttokens.ClientGroupToken;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.logging.AuditLogger;
import org.bouncycastle.util.encoders.Base64;
import org.jose4j.base64url.Base64Url;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwx.CompactSerializer;
import org.jose4j.jwx.Headers;
import org.jose4j.lang.StringUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.*;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public class JcaSigner implements Signer {

    private final Base64Url base64Url = new Base64Url();
    private final AbstractClientToken clientToken;
    private final KeyService keyService;

    @Override
    public JcaSigningResult sign(JsonWebSignature jws, UUID privateKid, SignatureAlgorithm signatureAlgorithm) {
        byte[] bytesToSign = getBytesToSign(jws);
        String signature;
        try {
            signature = sign(privateKid, signatureAlgorithm, true, bytesToSign);
        } catch (KeyNotFoundException e) {
            throw new SigningFailedException("Failed to get the private key to create signature.", e);
        }
        return new JcaSigningResult(jws, signature);
    }

    @Override
    public String sign(byte[] bytesToSign, UUID privateKid, SignatureAlgorithm signatureAlgorithm) {
        String signature;
        try {
            signature = sign(privateKid, signatureAlgorithm, false, bytesToSign);
        } catch (KeyNotFoundException e) {
            throw new SigningFailedException("Failed to get the private key to create signature.", e);
        }
        return signature;
    }

    private String sign(UUID privateKid, SignatureAlgorithm algorithm, boolean urlEncoding, byte[] payload) throws KeyNotFoundException {
        String clientId = clientToken instanceof ClientToken ? ((ClientToken) clientToken).getClientIdClaim().toString() : "";
        String clientGroupId = clientToken.getClientGroupIdClaim().toString();
        try {
            PrivateKey privateKey = getPrivateSigningKey(privateKid);
            Signature signature = Signature.getInstance(algorithm.getJvmAlgorithm());
            signature.initSign(privateKey);
            signature.update(payload);

            String encodedSignature;
            String message;
            if (urlEncoding) {
                encodedSignature = base64Url.base64UrlEncode(signature.sign());
                message = String.format("Successfully signed payload for [client: %s or client-group: %s], kid: %s and algorithm: %s, resulting url encoded signature: %s",
                        clientId, clientGroupId, privateKid, algorithm.getJvmAlgorithm(), encodedSignature);
            } else {
                encodedSignature = Base64.toBase64String(signature.sign());
                message = String.format("Successfully signed payload for [client: %s or client-group: %s], kid: %s and algorithm: %s, resulting encoded signature: %s",
                        clientId, clientGroupId, privateKid, algorithm.getJvmAlgorithm(), encodedSignature);
            }

            AuditLogger.logSuccess(message, payload);
            return encodedSignature;
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException ex) {
            String message = String.format("Failed creating signature for payload for [client: %s or client-group: %s], kid: %s and algorithm: %s",
                    clientId, clientGroupId, privateKid, algorithm.getJvmAlgorithm());
            AuditLogger.logError(message, payload, ex);

            throw new SigningFailedException(message, ex);
        }
    }

    private PrivateKey getPrivateSigningKey(UUID privateKid) throws KeyNotFoundException {
        if (clientToken instanceof ClientToken) {
            return keyService.getPrivateSigningKey((ClientToken) clientToken, privateKid);
        }
        if (clientToken instanceof ClientGroupToken) {
            return keyService.getPrivateSigningKey((ClientGroupToken) clientToken, privateKid);
        }
        throw new IllegalStateException("Unsupported ClientToken type: " + clientToken.getClass());
    }

    private byte[] getBytesToSign(JsonWebSignature jws) {
        try {
            return getSigningInputBytes(jws);
        } catch (IOException e) {
            throw new SigningFailedException("Failed to create the byte array to sign", e);
        }
    }

    /**
     * This method is copied from the Jose4j sources to ensure that we get exactly the right bytes to create the
     * signature for. See org.jose4j.jws.JsonWebSignature#getSigningInputBytes()
     */
    private byte[] getSigningInputBytes(JsonWebSignature jws) throws IOException {
        if (!this.isRfc7797UnencodedPayload(jws.getHeaders())) {
            String signingInputString = CompactSerializer.serialize(jws.getHeaders().getEncodedHeader(), jws.getEncodedPayload());
            return StringUtil.getBytesAscii(signingInputString);
        } else {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            os.write(StringUtil.getBytesAscii(jws.getHeaders().getEncodedHeader()));
            os.write(46);
            os.write(jws.getUnverifiedPayloadBytes());
            return os.toByteArray();
        }
    }

    /**
     * This method is copied from the Jose4j sources to ensure that we get exactly the right bytes to create the
     * signature for. See org.jose4j.jws.JsonWebSignature#isRfc7797UnencodedPayload()
     */
    private boolean isRfc7797UnencodedPayload(Headers headers) {
        Object b64 = headers.getObjectHeaderValue("b64");
        return b64 instanceof Boolean && !(Boolean) b64;
    }
}
