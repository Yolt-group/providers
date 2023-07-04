package com.yolt.providers.web.cryptography;

import com.amazonaws.cloudhsm.jce.jni.exception.ProviderInitializationException;
import com.amazonaws.cloudhsm.jce.provider.CloudHsmProvider;
import com.amazonaws.cloudhsm.jce.provider.KeyStoreWithAttributes;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.AbstractClientToken;
import nl.ing.lovebird.clienttokens.ClientToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * CloudHSMKeyService is used to retrieve private transport keys from the AWS Cloud HSM. When cloudHSM is enabled, this
 * service becomes the primary {@link KeyService}. When a private transport key has not been created in the HSM, it will
 * fallback to Vault KV.
 */
@Primary
@Service
@ConditionalOnProperty("yolt.providers.cloudHSM.enabled")
@Slf4j
public class CloudHSMKeyService implements KeyService {

    static final UUID ING_NV_GROUPID = UUID.fromString("141f08f5-cc7a-483e-beeb-3e28244404b1");
    static final UUID YOLT_CLIENTID = UUID.fromString("297ecda4-fd60-4999-8575-b25ad23b249c");
    static final UUID YOLT_GROUP_ID = UUID.fromString("0005291f-68bb-4d5f-9a3f-7aa330fb7641");
    static final UUID YTS_CLIENT_GROUP_ID = UUID.fromString("f767b2f9-5c90-4a4e-b728-9c9c8dadce4f");
    private static final String EXCEPTION_OCCURRED_WHILE_LOOKING_UP_KEY_IN_CLOUD_HSM_KEYSTORE = "Exception occurred while looking up key in AWS Cloud HSM keystore";

    private final ConcurrentMap<PrivateKeyReference, PrivateKey> keyCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<PrivateKey, PrivateKeyReference> referenceCache = new ConcurrentHashMap<>();

    private final HSMCredentials hsmCredentials;
    private final AuthProvider provider;
    private final KeyStore keystore;

    CloudHSMKeyService(HSMCredentials hsmCredentials, AuthProvider provider, KeyStore keystore) {
        this.hsmCredentials = hsmCredentials;
        this.provider = provider;
        this.keystore = keystore;
    }


    @Autowired
    public CloudHSMKeyService(VaultService vaultService) throws ProviderInitializationException, LoginException, IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
        this(
                vaultService.getHSMCredentials(),
                cloudHsmProviderInstance(),
                cloudHsmKeyStoreInstance()
        );
    }

    private static AuthProvider cloudHsmProviderInstance() throws IOException, ProviderInitializationException, LoginException {
        AuthProvider provider = (AuthProvider) Security.getProvider(CloudHsmProvider.PROVIDER_NAME);
        if (provider == null) {
            provider = new CloudHsmProvider();
        }
        Security.addProvider(provider);
        return provider;
    }

    private static KeyStore cloudHsmKeyStoreInstance() throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStoreWithAttributes keystore = KeyStoreWithAttributes.getInstance(CloudHsmProvider.PROVIDER_NAME);
        keystore.load(null, null);
        return keystore;
    }

    @Override
    public PrivateKey getPrivateTransportKey(AbstractClientToken clientToken, UUID kid) throws KeyNotFoundException {
        PrivateKey privateKey = getPrivateKeyFromCache(clientToken, kid);
        if (privateKey != null) {
            return privateKey;
        }
        throw new KeyNotFoundException("Unable to retrieve private transport key for kid: " + kid);
    }

    @Override
    public PrivateKey getPrivateSigningKey(AbstractClientToken clientToken, UUID kid) throws KeyNotFoundException {
        PrivateKey privateKey = getPrivateKeyFromCache(clientToken, kid);
        if (privateKey != null) {
            return privateKey;
        }
        throw new KeyNotFoundException("Unable to retrieve private signing key for kid: " + kid);

    }

    private PrivateKey getPrivateKeyFromCache(AbstractClientToken clientToken, UUID kid) {
        PrivateKeyReference reference = new PrivateKeyReference(clientToken.getClientGroupIdClaim(), kid);
        return keyCache.computeIfAbsent(reference, privateKeyReference -> {
            PrivateKey key = getKeyFromHSM(clientToken, kid)
                    .map(PrivateKey.class::cast)
                    .orElse(null);
            // Note: Each look up in the HSM will create a new instance of a
            // key, but keyCache.computeIfAbsent is atomic. This guarantees
            // that for every reference there will only ever by one instance
            // of each key in this application context.
            if (key != null) {
                referenceCache.put(key, reference);
            }
            return key;
        });
    }

    Optional<PrivateKeyReference> getPrivateKeyReference(PrivateKey privateKey){
        return Optional.ofNullable(referenceCache.get(privateKey));
    }

    @PostConstruct
    void login() throws LoginException {
        String userName = hsmCredentials.getUsername();
        String password = hsmCredentials.getPassword();
        ApplicationCallBackHandler loginHandler = new ApplicationCallBackHandler(userName + ":" + password);
        // This can throw an LoginException which will block the app from starting up. This is wanted behavior, because once we
        // rollover credentials for CloudHSM we can keep old instances, with old credentials, running until the new credentials work fine.
        provider.login(null, loginHandler);
    }

    @RequiredArgsConstructor
    @EqualsAndHashCode
    static class ApplicationCallBackHandler implements CallbackHandler {

        private final String cloudhsmPin;

        @Override
        public void handle(Callback[] callbacks) {
            for (Callback callback : callbacks) {
                if (callback instanceof PasswordCallback) {
                    PasswordCallback pc = (PasswordCallback) callback;
                    pc.setPassword(cloudhsmPin.toCharArray());
                }
            }
        }
    }

    @PreDestroy
    void logout() {
        try {
            provider.logout();
        } catch (LoginException e) {
            // Logout of the HSM when the pod is destroyed.
            // As a session with the HSM has a TTL of 10min, it is likely that our session was already closed making logging as info enough.
            log.info("Logout of HSM failed.", e);
        }
    }

    private Optional<Key> getKeyFromHSM(AbstractClientToken clientToken, UUID kid) {
        if (kid == null) {
            return Optional.empty();
        }
        String alias = keyAlias(clientToken, kid);
        Key key = null;
        try {
            key = getKeyByLabel(alias);
        } catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException ex) {
            log.warn(EXCEPTION_OCCURRED_WHILE_LOOKING_UP_KEY_IN_CLOUD_HSM_KEYSTORE, ex);
        }

        if (key == null
            && clientToken instanceof ClientToken
            && ((ClientToken) clientToken).getClientIdClaim().equals(YOLT_CLIENTID)) {
            log.info("Retrying to find key using the ING N.V. Client Group instead of the Yolt Client Group Id");
            try {
                key = getKeyByLabel(ING_NV_GROUPID + "_" + kid);
            } catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException ex) {
                log.warn(EXCEPTION_OCCURRED_WHILE_LOOKING_UP_KEY_IN_CLOUD_HSM_KEYSTORE, ex);
            }
        }

        if (key == null && YTS_CLIENT_GROUP_ID.equals(clientToken.getClientGroupIdClaim())) {
            log.info("Retrying to find key using the Yolt Client Group ID instead of the YTS Client Group ID");
            try {
                key = getKeyByLabel(YOLT_GROUP_ID + "_" + kid);
            } catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException ex) {
                log.warn(EXCEPTION_OCCURRED_WHILE_LOOKING_UP_KEY_IN_CLOUD_HSM_KEYSTORE, ex);
            }
        }

        return Optional.ofNullable(key);
    }

    private Key getKeyByLabel(String keyAlias) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException {
        return keystore.getKey(keyAlias, null);
    }

    private String keyAlias(final AbstractClientToken clientToken, final UUID kid) {
        return clientToken.getClientGroupIdClaim().toString() + "_" + kid.toString();
    }

    @Value
    static class PrivateKeyReference {
        UUID clientGroupId;
        UUID kid;
    }
}
