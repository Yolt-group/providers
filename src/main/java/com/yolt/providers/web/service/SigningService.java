package com.yolt.providers.web.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yolt.providers.web.cryptography.signing.JcaSigner;
import com.yolt.providers.web.cryptography.signing.JcaSignerFactory;
import com.yolt.providers.web.exception.InvalidInputException;
import com.yolt.securityutils.signing.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.clienttokens.ClientToken;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SigningService {

    private static final String PATTERN = "\\{\"algorithm\":\"[A-Z0-9]{5}\",\"alias\":\\{\"realmId\":\"\\w+:\\w+:\\w+\"," +
            "\"type\":\"EIDAS\",\"value\":\".*\"},\"certificate\":\"" +
            "[A-Za-z0-9+/]*={0,2}\",\"memberId\":\"\\w+:\\w+:\\w+\"}";

    private static final Pattern URL_PATTERN = Pattern.compile("(https?:\\/\\/(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|https?:\\/\\/(?:www\\.|(?!www))[a-zA-Z0-9]+\\.[^\\s]{2,}|www\\.[a-zA-Z0-9]+\\.[^\\s]{2,})");

    private static final int PAYLOAD_SIZE_LIMIT = 10000;
    private static final List<String> NATIONWIDE_AVAILABLE_GRANT_TYPES = Arrays.asList("authorization_code", "refresh_token", "client_credentials");

    private final Clock clock;
    private final JcaSignerFactory jcaSignerFactory;

    public String signPayload(String payload,
                              String signingKeyId,
                              String jvmAlgorithm,
                              ClientToken clientToken) {
        if (!payload.matches(PATTERN)) {
            throw new InvalidInputException("Invalid payload form");
        }
        JcaSigner signer = jcaSignerFactory.getForClientToken(clientToken);
        SignatureAlgorithm algorithm = SignatureAlgorithm.findByJvmAlgorithmOrThrowException(jvmAlgorithm);

        return signer.sign(payload.getBytes(), UUID.fromString(signingKeyId), algorithm);
    }

    public String signNationwidePayload(final String payload, final String signingKeyId, final String jvmAlgorithm, final ClientToken clientToken) {
        validateOBRegistrationBody(payload);
        JcaSigner signer = jcaSignerFactory.getForClientToken(clientToken);
        SignatureAlgorithm algorithm = SignatureAlgorithm.findByJvmAlgorithmOrThrowException(jvmAlgorithm);

        return signer.sign(payload.getBytes(), UUID.fromString(signingKeyId), algorithm);
    }

    private void validateOBRegistrationBody(final String payload) {
        try {
            validateSize(payload);
            JsonNode json = new ObjectMapper().readTree(payload);
            validateSSA(json);
            validateGrantTypes(json);
            validateScopes(json);
            validateIss(json);
            validateExp(json);
            validateIat(json);
            validateAuthMethod(json);
            validateSoftwareId(json);
            validateRedirectUris(json);

        } catch (JsonProcessingException e) {
            throw new InvalidInputException("Invalid payload json form");
        }
    }

    private void validateSize(final String payload) {
        if (payload == null) {
            throw new InvalidInputException("Payload is null");
        }
        if (payload.length() > PAYLOAD_SIZE_LIMIT) {
            throw new InvalidInputException("Payload too big. Max " + PAYLOAD_SIZE_LIMIT + " characters");
        }
    }

    private void validateRedirectUris(final JsonNode json) {
        JsonNode redirectUrisField = json.get("redirect_uris");
        if (!(redirectUrisField instanceof ArrayNode)) {
            throw new InvalidInputException("Missing redirectUris");
        }
        ArrayNode redirectUris = (ArrayNode) redirectUrisField;
        if (redirectUris.size() == 0) {
            throw new InvalidInputException("No redirect_uris defined");
        }
        for (int i = 0; i < redirectUris.size(); i++) {
            String jsonNode = redirectUris.get(i).textValue();
            if (null == jsonNode) {
                throw new InvalidInputException("Missing redirectUris");
            }
            if (!URL_PATTERN.matcher(jsonNode).matches()) {
                throw new InvalidInputException("Unknown redirectUri: " + jsonNode);
            }
        }
    }

    private void validateSoftwareId(final JsonNode json) {
        isNotEmpty(json, "software_id");
    }

    private void isNotEmpty(final JsonNode json, final String key) {
        JsonNode jsonNode = json.get(key);
        if (null == jsonNode) {
            throw new InvalidInputException("Missing " + key);
        }
        String value = jsonNode.textValue();
        if (null == value) {
            throw new InvalidInputException("Missing " + key);
        }
        if (value.isEmpty()) {
            throw new InvalidInputException(key + " is empty");
        }
    }

    private void validateAuthMethod(final JsonNode json) {
        isNotEmpty(json, "token_endpoint_auth_method");
    }

    private void validateIat(final JsonNode json) {
        JsonNode iatField = json.get("iat");
        if (null == iatField || !iatField.canConvertToLong()) {
            throw new InvalidInputException("Missing iat");
        }
        long iat = iatField.longValue();
        if (Instant.ofEpochSecond(iat).isAfter(Instant.now(clock))) {
            throw new InvalidInputException("Iat should be past date");
        }
    }

    private void validateExp(final JsonNode json) {
        JsonNode expField = json.get("exp");
        if (null == expField || !expField.canConvertToLong()) {
            throw new InvalidInputException("Missing exp");
        }
        long exp = expField.longValue();

        if (Instant.ofEpochSecond(exp).isBefore(Instant.now(clock))) {
            throw new InvalidInputException("Exp should be future date");
        }
    }

    private void validateIss(final JsonNode json) {
        isNotEmpty(json, "iss");
    }

    private void validateGrantTypes(final JsonNode json) {
        JsonNode grantTypesField = json.get("grant_types");
        if (!(grantTypesField instanceof ArrayNode)) {
            throw new InvalidInputException("Missing grantTypes");
        }
        ArrayNode grantTypes = (ArrayNode) grantTypesField;

        if (grantTypes.size() == 0) {
            throw new InvalidInputException("No grantTypes defined");
        }
        for (int i = 0; i < grantTypes.size(); i++) {
            JsonNode o = grantTypes.get(i);
            String grantType = o.textValue();
            if (grantType == null) {
                throw new InvalidInputException("Missing grantTypes");
            }
            if (!NATIONWIDE_AVAILABLE_GRANT_TYPES.contains(grantType)) {
                throw new InvalidInputException("Unknown grantType: " + grantType);
            }
        }
    }

    private void validateScopes(final JsonNode json) {
        isNotEmpty(json, "scope");
    }

    private void validateSSA(final JsonNode json) {
        try {
            JsonNode softwareStatementField = json.get("software_statement");
            if (null == softwareStatementField) {
                throw new InvalidInputException("Missing ssa");
            }
            String ssa = softwareStatementField.textValue();
            if (null == ssa) {
                throw new InvalidInputException("Missing ssa");
            }
            JsonWebSignature.fromCompactSerialization(ssa);
        } catch (JoseException e) {
            throw new InvalidInputException("Invalid software_statement form, it should be jws", e);
        }
    }
}
