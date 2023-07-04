package com.yolt.providers.web.cryptography;

import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.AbstractClientToken;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.logging.AuditLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import javax.validation.constraints.NotNull;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * This service is used to retrieve private keys for setting up mutual TLS.
 * It uses the lovebird-commons code for authenticating to vault.
 */
@Service
@Slf4j
public class VaultService implements KeyService {

    // Private Keys get created and stored from the crypto service, we can only read keys from providers.
    private static final String KEY_VALUE_STORE_NAME = "crypto";

    private static final String CLOUDHSM_CREDENTIALS_PATH = "%s/k8s/pods/cloudhsm/kv/cloudhsm-users/%s/data/%s/%s";
    private static final String HSM_PARTITION = "HSM_PARTITION";
    private static final String HSM_USER = "HSM_USER";
    private static final String HSM_PASSWORD = "HSM_PASSWORD"; // NOSONAR: this is not a password

    private final Supplier<VaultTemplate> vaultTemplateSupplier;
    private final String cloudHSMLocation;
    private final String namespace;
    private final String environment;
    private final String applicationName;

    public VaultService(Supplier<VaultTemplate> vaultTemplateSupplier,
                        @Value("${cluster.cloudhsm.location}") String cloudHSMLocation,
                        @Value("${yolt.deployment.namespace}") String namespace,
                        @Value("${yolt.deployment.environment}") String environment,
                        @Value("${spring.application.name}") String applicationName) {
        this.vaultTemplateSupplier = vaultTemplateSupplier;
        this.cloudHSMLocation = cloudHSMLocation;
        this.namespace = namespace;
        this.environment = environment;
        this.applicationName = applicationName;
    }

    /**
     * The private transport key is being retrieved from the vault key-value store on the key reference; the kid.
     * A check is done to make sure that the private key is part of the client (or client-group) that we expect it to be.
     *
     * @param clientToken The Client-Group which owns the private key.
     * @param kid         The reference to the vault secret which includes the private key.
     * @return The Private Key
     */
    @Override
    public @NotNull PrivateKey getPrivateTransportKey(AbstractClientToken clientToken, UUID kid) throws KeyNotFoundException {
        UUID clientIdClaim = clientToken instanceof ClientToken ? ((ClientToken) clientToken).getClientIdClaim() : null;
        return doGetPrivateKey(kid, "transport", clientIdClaim, clientToken.getClientGroupIdClaim());
    }

    /**
     * The private signing key is being retrieved from the vault key-value store on the key reference; the kid.
     * A check is done to make sure that the private key is part of the client (or client-group) that we expect it to be.
     *
     * @param clientToken The Client-Group which owns the private key.
     * @param kid         The reference to the vault secret which includes the private key.
     * @return The Private Key
     */

    @Override
    @NotNull
    public PrivateKey getPrivateSigningKey(AbstractClientToken clientToken, UUID kid) throws KeyNotFoundException {
        UUID clientIdClaim = clientToken instanceof ClientToken ? ((ClientToken) clientToken).getClientIdClaim() : null;
        return doGetPrivateKey(kid, "signing", clientIdClaim, clientToken.getClientGroupIdClaim());
    }

    private PrivateKey doGetPrivateKey(UUID kid, String keytype, UUID clientIdClaim, UUID clientGroupIdClaim) throws KeyNotFoundException {
        String clientId = clientIdClaim != null ? clientIdClaim.toString() : null;
        String clientGroupId = clientGroupIdClaim.toString();

        String secretsPath = getSecretsBasePath(keytype, kid);

        VaultResponse readSecrets = vaultTemplateSupplier.get().read(secretsPath);
        if (readSecrets == null) {
            AuditLogger.logFailure(String.format("Error retrieving %s private key with kid: %s from Vault.", keytype, kid), null);
            String error = String.format("Key not found for [client-id: %s or client-group-id: %s] and kid: %s. Secret map didn't exist.",
                    clientId, clientGroupId, kid);
            throw new KeyNotFoundException(error);
        }

        Map<String, String> data = (Map<String, String>) readSecrets.getData().get("data");

        if ((!Objects.equals(clientId, data.get("client-id")) || clientId == null)
                && !clientGroupId.equals(data.get("client-group-id"))) {
            AuditLogger.logFailure(String.format(
                    "%s private key with kid: %s does not match client-id: %s or client-group-id: %s",
                    keytype, kid, clientId, clientGroupId
            ), null);
            throw new KeyNotFoundException(String.format(
                    "Key not found for [client-id: %s or client-group-id: %s] and kid: %s. " +
                            "client-id does not match (actual id: %s). " +
                            "client-group-id does not match (actual id: %s).",
                    clientId, clientGroupId, kid, data.get("client-id"), data.get("client-group-id")
            ));
        }

        try {
            KeyFactory keyFactory = KeyFactory.getInstance(data.get("key-algorithm"));
            PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(data.get("private-key"))));

            AuditLogger.logSuccess(String.format("Successfully retrieved %s private key with kid: %s from Vault.", keytype, kid), null);

            return privateKey;
        } catch (InvalidKeySpecException | NoSuchAlgorithmException ex) {
            AuditLogger.logFailure(String.format("Error deserializing private key with kid: %s", kid), null);
            log.error("Failed to deserialize private key from Vault.", ex);
            throw new KeyNotFoundException(String.format(
                    "Key not found for [client-id: %s or client-group-id: %s] and kid: %s. Private key couldn't be deserialized",
                    clientId, clientGroupId, kid
            ));
        }
    }

    HSMCredentials getHSMCredentials() {
        String secretsPath = String.format(CLOUDHSM_CREDENTIALS_PATH, cloudHSMLocation, applicationName, environment, namespace);

        Map<String, String> data = (Map<String, String>) vaultTemplateSupplier.get().read(secretsPath).getData().get("data");

        return new HSMCredentials(data.get(HSM_PARTITION), data.get(HSM_USER), data.get(HSM_PASSWORD));
    }

    private String getSecretsBasePath(String keyType, UUID keyId) {
        return String.format("%s/k8s/pods/%s/kv/%s/%s/data/%s", environment, namespace, KEY_VALUE_STORE_NAME, keyType, keyId.toString());
    }
}
