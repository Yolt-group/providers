package com.yolt.providers.dummy;

import com.yolt.providers.common.ais.DataProviderResponse;
import com.yolt.providers.common.ais.url.UrlCreateAccessMeansRequest;
import com.yolt.providers.common.ais.url.UrlFetchDataRequest;
import com.yolt.providers.common.ais.url.UrlGetLoginRequest;
import com.yolt.providers.common.ais.url.UrlRefreshAccessMeansRequest;
import com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans;
import com.yolt.providers.common.domain.authenticationmeans.keymaterial.DistinguishedNameElement;
import com.yolt.providers.common.domain.authenticationmeans.keymaterial.KeyAlgorithm;
import com.yolt.providers.common.domain.authenticationmeans.keymaterial.KeyMaterialRequirements;
import com.yolt.providers.common.domain.authenticationmeans.keymaterial.KeyRequirements;
import com.yolt.providers.common.domain.consenttesting.ConsentValidityRules;
import com.yolt.providers.common.domain.dynamic.AccessMeansOrStepDTO;
import com.yolt.providers.common.domain.dynamic.step.RedirectStep;
import com.yolt.providers.common.exception.ProviderFetchDataException;
import com.yolt.providers.common.exception.TokenInvalidException;
import com.yolt.providers.common.providerinterface.UrlDataProvider;
import com.yolt.providers.common.versioning.ProviderVersion;
import com.yolt.securityutils.signing.SignatureAlgorithm;
import nl.ing.lovebird.providershared.AccessMeansDTO;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.yolt.providers.common.versioning.ProviderVersion.VERSION_1;

@Service
public class DummyTestProvider implements UrlDataProvider {

    public static final String API_KEY_NAME = "api-key";
    public static final String API_SECRET_NAME = "api-secret";

    public static final String CLIENT_ID_NAME = "clientId";
    public static final String AUDIENCE_NAME = "audience";
    public static final String INSTITUTION_ID_NAME = "institutionId";
    public static final String PUBLIC_KEY_NAME = "publicKey";
    public static final String PRIVATE_KEY_NAME = "privateKey";
    public static final String PRIVATE_KEY_ID_NAME = "privateKid";
    public static final String CERTIFICATE_ID_NAME = "certificateId";
    public static final String TRANSPORT_PRIVATE_KEY_ID_NAME = "transportPrivateKid";
    public static final String TRANSPORT_CLIENT_CERTIFICATE_NAME = "transportClientCertificate";

    @Override
    public DataProviderResponse fetchData(UrlFetchDataRequest urlFetchData)
            throws ProviderFetchDataException, TokenInvalidException {
        return null;
    }

    @Override
    public RedirectStep getLoginInfo(UrlGetLoginRequest urlGetLogin) {

        RedirectStep step = new RedirectStep("https://ingress.integration.yolt.io/stubs/yoltprovider/authorize?x=https://www.yolt.com/callback&" + urlGetLogin.getState());
        return step;
    }

    @Override
    public AccessMeansOrStepDTO createNewAccessMeans(UrlCreateAccessMeansRequest urlCreateAccessMeans) {
        return null;
    }

    @Override
    public AccessMeansDTO refreshAccessMeans(UrlRefreshAccessMeansRequest urlRefreshAccessMeans)
            throws TokenInvalidException {
        return null;
    }

    @Override
    public String getProviderIdentifier() {
        return "TEST_IMPL_OPENBANKING_MOCK";
    }

    @Override
    public String getProviderIdentifierDisplayName() {
        return "Test Impl OpenBanking";
    }

    @Override
    public Map<String, TypedAuthenticationMeans> getTypedAuthenticationMeans() {
        //TODO: Implemented to test flow with adding authentication means for two separate providers -
        // not actual implementation - copied from OpenbankingTestImplProvider replace with correct when adapting to types authentication means
        Map<String, TypedAuthenticationMeans> authenticationMeans = new HashMap<>();
        authenticationMeans.put(AUDIENCE_NAME, TypedAuthenticationMeans.AUDIENCE_STRING);
        authenticationMeans.put(CLIENT_ID_NAME, TypedAuthenticationMeans.CLIENT_ID_UUID);
        authenticationMeans.put(INSTITUTION_ID_NAME, TypedAuthenticationMeans.INSTITUTION_ID_STRING);
        authenticationMeans.put(PRIVATE_KEY_NAME, TypedAuthenticationMeans.PRIVATE_KEY_PEM);
        authenticationMeans.put(PUBLIC_KEY_NAME, TypedAuthenticationMeans.PUBLIC_KEY_PEM);
        authenticationMeans.put(PRIVATE_KEY_ID_NAME, TypedAuthenticationMeans.KEY_ID);
        authenticationMeans.put(CERTIFICATE_ID_NAME, TypedAuthenticationMeans.CERTIFICATE_ID);
        authenticationMeans.put(TRANSPORT_PRIVATE_KEY_ID_NAME, TypedAuthenticationMeans.KEY_ID);
        authenticationMeans.put(TRANSPORT_CLIENT_CERTIFICATE_NAME, TypedAuthenticationMeans.CLIENT_TRANSPORT_CERTIFICATE_PEM);
        return authenticationMeans;
    }

    @Override
    public ConsentValidityRules getConsentValidityRules() {
        return ConsentValidityRules.EMPTY_RULES_SET;
    }

    @Override
    public Optional<KeyRequirements> getSigningKeyRequirements() {
        Set<KeyAlgorithm> supportedAlgorithms = new HashSet<>();
        supportedAlgorithms.add(KeyAlgorithm.RSA2048);
        supportedAlgorithms.add(KeyAlgorithm.RSA4096);

        Set<SignatureAlgorithm> supportedSignatureAlgorithms = new HashSet<>();
        supportedSignatureAlgorithms.add(SignatureAlgorithm.SHA256_WITH_RSA);

        List<DistinguishedNameElement> requiredDNs = new ArrayList<>();
        requiredDNs.add(new DistinguishedNameElement("C", "GB", "", true));
        requiredDNs.add(new DistinguishedNameElement("O", "OpenBanking", "", false));
        requiredDNs.add(new DistinguishedNameElement("OU"));
        requiredDNs.add(new DistinguishedNameElement("CN"));

        KeyMaterialRequirements keyRequirements = new KeyMaterialRequirements(supportedAlgorithms, supportedSignatureAlgorithms, requiredDNs);
        KeyRequirements signingRequirements = new KeyRequirements(keyRequirements, PRIVATE_KEY_ID_NAME);

        return Optional.of(signingRequirements);
    }

    @Override
    public Optional<KeyRequirements> getTransportKeyRequirements() {
        Set<KeyAlgorithm> supportedAlgorithms = new HashSet<>();
        supportedAlgorithms.add(KeyAlgorithm.RSA2048);
        supportedAlgorithms.add(KeyAlgorithm.RSA4096);

        Set<SignatureAlgorithm> supportedSignatureAlgorithms = new HashSet<>();
        supportedSignatureAlgorithms.add(SignatureAlgorithm.SHA256_WITH_RSA);

        List<DistinguishedNameElement> requiredDNs = new ArrayList<>();
        requiredDNs.add(new DistinguishedNameElement("C", "GB", "", true));
        requiredDNs.add(new DistinguishedNameElement("O", "OpenBanking", "", false));
        requiredDNs.add(new DistinguishedNameElement("OU"));
        requiredDNs.add(new DistinguishedNameElement("CN"));

        KeyMaterialRequirements keyRequirements = new KeyMaterialRequirements(supportedAlgorithms, supportedSignatureAlgorithms, requiredDNs);
        KeyRequirements transportKeyRequirements = new KeyRequirements(keyRequirements, "transportPrivateKid", "transportClientCertificate");

        return Optional.of(transportKeyRequirements);
    }

    @Override
    public ProviderVersion getVersion() {
        return VERSION_1;
    }
}
