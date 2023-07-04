package com.yolt.providers.web.service;

import com.yolt.providers.common.ais.url.*;
import com.yolt.providers.common.cryptography.RestTemplateManager;
import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import com.yolt.providers.common.domain.dynamic.AccessMeansOrStepDTO;
import com.yolt.providers.common.domain.dynamic.step.Step;
import com.yolt.providers.common.providerdetail.dto.AisSiteDetails;
import com.yolt.providers.common.providerinterface.UrlDataProvider;
import com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService;
import com.yolt.providers.web.controller.dto.*;
import com.yolt.providers.web.cryptography.signing.JcaSigner;
import com.yolt.providers.web.cryptography.signing.JcaSignerFactory;
import com.yolt.providers.web.cryptography.transport.MutualTLSRestTemplateManagerCache;
import com.yolt.providers.web.encryption.AesEncryptionUtil;
import com.yolt.providers.web.service.circuitbreaker.CircuitBreakerAisService;
import com.yolt.providers.web.service.configuration.VersionType;
import com.yolt.providers.web.service.dto.FetchDataResultDTO;
import com.yolt.providers.web.service.dto.IngestionAccountDTO;
import com.yolt.providers.web.sitedetails.dto.AisProviderSiteData;
import com.yolt.providers.web.sitedetails.sites.SiteDetailsService;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.ClientUserToken;
import nl.ing.lovebird.providerdomain.ProviderAccountDTO;
import nl.ing.lovebird.providershared.AccessMeansDTO;
import nl.ing.lovebird.providershared.ProviderServiceResponseStatus;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.yolt.providers.web.configuration.ApplicationConfiguration.ASYNC_PROVIDER_FETCH_DATA_EXECUTOR;
import static com.yolt.providers.web.service.ProviderVersioningUtil.getVersionType;
import static com.yolt.providers.web.service.configuration.VersionType.EXPERIMENTAL;
import static com.yolt.providers.web.service.configuration.VersionType.STABLE;
import static net.logstash.logback.marker.Markers.append;
import static nl.ing.lovebird.logging.MDCContextCreator.*;
import static nl.ing.lovebird.providerdomain.ServiceType.AIS;

@Slf4j
@Service
public class ProviderService {

    public static final String PROVIDER_MDC_KEY = "provider";

    private final ProviderFactoryService providerFactoryService;
    private final ClientAuthenticationMeansService clientAuthenticationMeansService;
    private final TransactionsDataLimiter transactionsDataLimiter;
    private final ProviderServiceResponseProducer providerServiceResponseProducer;
    private final JcaSignerFactory jcaSignerFactory;
    private final MutualTLSRestTemplateManagerCache restTemplateManagerCache;
    private final AccountsFilterService accountsFilterService;
    private final AccountsPostProcessingService accountsPostProcessingService;
    private final AccountsProducer accountsProducer;
    private final ProviderVaultKeys vaultKeys;
    private final CircuitBreakerAisService circuitBreakerAisService;
    private final SiteDetailsService siteDetailsService;

    private static final Marker HAPPY_FLOW_MARKER = append("happy-flow", "true");
    private static final String FAULTY_KEEBO_REDIRECT_URL_PART = "https://mobile.keebo.com/";
    private static final String PROPER_KEEBO_REDIRECT_URL_PART = "https://mobile.keebo.com";
    private static final String FETCH_DATA_ENDPOINT_VALUE = "/{provider}/fetch-data";

    public ProviderService(final ProviderFactoryService providerFactoryService,
                           final ClientAuthenticationMeansService clientAuthenticationMeansService,
                           final TransactionsDataLimiter transactionsDataLimiter,
                           final ProviderServiceResponseProducer providerServiceResponseProducer,
                           final JcaSignerFactory jcaSignerFactory,
                           final MutualTLSRestTemplateManagerCache restTemplateManagerCache,
                           final AccountsFilterService accountsFilterService,
                           final AccountsPostProcessingService accountsPostProcessingService,
                           final AccountsProducer accountsProducer,
                           final ProviderVaultKeys vaultKeys,
                           final CircuitBreakerAisService circuitBreakerAisService,
                           final SiteDetailsService siteDetailsService) {
        this.providerFactoryService = providerFactoryService;
        this.clientAuthenticationMeansService = clientAuthenticationMeansService;
        this.transactionsDataLimiter = transactionsDataLimiter;
        this.providerServiceResponseProducer = providerServiceResponseProducer;
        this.jcaSignerFactory = jcaSignerFactory;
        this.restTemplateManagerCache = restTemplateManagerCache;
        this.accountsFilterService = accountsFilterService;
        this.accountsPostProcessingService = accountsPostProcessingService;
        this.accountsProducer = accountsProducer;
        this.vaultKeys = vaultKeys;
        this.circuitBreakerAisService = circuitBreakerAisService;
        this.siteDetailsService = siteDetailsService;
    }

    public AccessMeansDTO refreshAccessMeans(final String provider,
                                             final RefreshAccessMeansDTO refreshAccessMeansDTO,
                                             final ClientToken clientToken,
                                             final UUID siteId,
                                             final boolean forceExperimentalVersion) {
        UUID clientId = clientToken.getClientIdClaim();
        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        UUID redirectUrlId = refreshAccessMeansDTO.getAuthenticationMeansReference().getRedirectUrlId();
        Map<String, BasicAuthenticationMean> authenticationMeans =
                clientAuthenticationMeansService.acquireAuthenticationMeans(provider, AIS, refreshAccessMeansDTO.getAuthenticationMeansReference());
        VersionType versionType = getVersionType(forceExperimentalVersion);
        UrlDataProvider dataProvider = providerFactoryService.getProvider(provider, UrlDataProvider.class, AIS, versionType);
        JcaSigner signer = jcaSignerFactory.getForClientToken(clientToken);
        RestTemplateManager restTemplateManager = restTemplateManagerCache.getForClientProvider(clientToken, AIS, provider, false, dataProvider.getVersion());
        UrlRefreshAccessMeansRequest urlRefreshAccessMeans = new UrlRefreshAccessMeansRequest(
                refreshAccessMeansDTO.getAccessMeansDTO(),
                authenticationMeans,
                signer,
                restTemplateManager,
                refreshAccessMeansDTO.getPsuIpAddress());

        return circuitBreakerAisService.refreshAccessMeans(clientToken, siteId, provider, redirectUrlId, dataProvider, urlRefreshAccessMeans, refreshAccessMeansDTO.getConsentCreationTime(), getConsentExpirationInDays(provider));
    }

    public Step getLoginInfo(final String provider,
                             final ApiGetLoginDTO apiGetLoginDTO,
                             final ClientToken clientToken,
                             final UUID siteId,
                             final boolean forceExperimentalVersion) {
        UUID clientId = clientToken.getClientIdClaim();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));
        UUID redirectUrlId = apiGetLoginDTO.getAuthenticationMeansReference().getRedirectUrlId();
        Map<String, BasicAuthenticationMean> authenticationMeans =
                clientAuthenticationMeansService.acquireAuthenticationMeans(provider, AIS, apiGetLoginDTO.getAuthenticationMeansReference());
        VersionType versionType = getVersionType(forceExperimentalVersion);
        UrlDataProvider dataProvider = providerFactoryService.getProvider(provider, UrlDataProvider.class, AIS, versionType);
        JcaSigner signer = jcaSignerFactory.getForClientToken(clientToken);
        RestTemplateManager restTemplateManager = restTemplateManagerCache.getForClientProvider(clientToken, AIS, provider, false, dataProvider.getVersion());
        UrlGetLoginRequest urlGetLogin = new UrlGetLoginRequest(
                apiGetLoginDTO.getBaseClientRedirectUrl(),
                apiGetLoginDTO.getState(),
                apiGetLoginDTO.getAuthenticationMeansReference(),
                authenticationMeans,
                null,
                signer,
                restTemplateManager,
                apiGetLoginDTO.getPsuIpAddress()
        );

        return circuitBreakerAisService.getLoginInfo(clientToken, siteId, provider, redirectUrlId, vaultKeys, dataProvider, urlGetLogin);
    }

    @Async(ASYNC_PROVIDER_FETCH_DATA_EXECUTOR)
    public void fetchDataAsync(final String provider,
                               final ApiFetchDataDTO apiFetchDataDTO,
                               final UUID siteId,
                               final ClientUserToken clientUserToken,
                               final boolean forceExperimentalVersion) {
        UUID userSiteId = apiFetchDataDTO.getUserSiteDataFetchInformation().getUserSiteId();
        UUID clientId = clientUserToken.getClientIdClaim();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(USER_SITE_ID_MDC_KEY, String.valueOf(userSiteId));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));
        MDC.put(ENDPOINT_MDC_KEY, FETCH_DATA_ENDPOINT_VALUE);

        final UUID providerRequestId = apiFetchDataDTO.getProviderRequestId();
        UUID redirectUrlId = apiFetchDataDTO.getAuthenticationMeansReference().getRedirectUrlId();
        Map<String, BasicAuthenticationMean> authenticationMeans =
                clientAuthenticationMeansService.acquireAuthenticationMeans(provider, AIS, apiFetchDataDTO.getAuthenticationMeansReference());
        VersionType versionType = getVersionType(forceExperimentalVersion);
        UrlDataProvider dataProvider = providerFactoryService.getProvider(provider, UrlDataProvider.class, AIS, versionType);
        JcaSigner signer = jcaSignerFactory.getForClientToken(clientUserToken);
        RestTemplateManager restTemplateManager = restTemplateManagerCache.getForClientProvider(clientUserToken, AIS, provider, true, dataProvider.getVersion());
        UrlFetchDataRequest urlFetchData = new UrlFetchDataRequest(
                apiFetchDataDTO.getUserId(),
                userSiteId,
                apiFetchDataDTO.getTransactionsFetchStartTime(),
                apiFetchDataDTO.getAccessMeans(),
                authenticationMeans,
                signer,
                restTemplateManager,
                apiFetchDataDTO.getAuthenticationMeansReference(),
                apiFetchDataDTO.getPsuIpAddress()
        );

        ProviderServiceResponseDTO providerServiceResponseDTO = circuitBreakerAisService.fetchDataAsync(
                clientUserToken,
                siteId,
                provider,
                redirectUrlId,
                dataProvider,
                urlFetchData,
                apiFetchDataDTO,
                accountsFilterService,
                transactionsDataLimiter,
                accountsPostProcessingService,
                providerRequestId
        );

        sendProviderServiceResponse(
                userSiteId,
                providerServiceResponseDTO,
                clientUserToken,
                apiFetchDataDTO.getActivityId(),
                provider,
                siteId
        );
    }

    private void sendProviderServiceResponse(final UUID userSiteId,
                                             final ProviderServiceResponseDTO providerServiceResponseDTO,
                                             final ClientUserToken clientUserToken,
                                             final UUID activityId,
                                             final String provider,
                                             final UUID siteId) {

        final List<ProviderAccountDTO> accounts = providerServiceResponseDTO.getAccounts();
        if (providerServiceResponseDTO.getProviderServiceResponseStatus() == ProviderServiceResponseStatus.FINISHED && accounts.isEmpty()) {
            providerServiceResponseProducer.sendNoSupportedAccountsMessage(userSiteId, clientUserToken);
            accountsProducer.publishAccountAndTransactions(activityId, userSiteId, siteId, Collections.emptyList(), clientUserToken, provider);
            return;
        }

        if (!accounts.isEmpty()) {
            List<IngestionAccountDTO> ingestionAccounts = providerServiceResponseDTO.getAccounts().stream()
                    .map(it -> new IngestionAccountDTO(clientUserToken.getUserIdClaim(), userSiteId, siteId, provider, it))
                    .collect(Collectors.toList());
            accountsProducer.publishAccountAndTransactions(activityId, userSiteId, siteId, ingestionAccounts, clientUserToken, provider);
        }
        providerServiceResponseProducer.sendMessage(
                new FetchDataResultDTO(providerServiceResponseDTO.getProviderRequestId(),
                        providerServiceResponseDTO.getProviderServiceResponseStatus()),
                clientUserToken);
    }

    public AccessMeansOrStepDTO createNewAccessMeans(final String provider,
                                                     final ApiCreateAccessMeansDTO apiCreateAccessMeansDTO,
                                                     final ClientToken clientToken,
                                                     final UUID siteId,
                                                     final boolean forceExperimentalVersion) {
        UUID clientId = clientToken.getClientIdClaim();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));
        UUID redirectUrlId = apiCreateAccessMeansDTO.getAuthenticationMeansReference().getRedirectUrlId();
        Map<String, BasicAuthenticationMean> authenticationMeans =
                clientAuthenticationMeansService.acquireAuthenticationMeans(provider, AIS, apiCreateAccessMeansDTO.getAuthenticationMeansReference());
        VersionType versionType = getVersionType(forceExperimentalVersion);
        UrlDataProvider dataProvider = providerFactoryService.getProvider(provider, UrlDataProvider.class, AIS, versionType);
        JcaSigner signer = jcaSignerFactory.getForClientToken(clientToken);
        RestTemplateManager restTemplateManager = restTemplateManagerCache.getForClientProvider(clientToken, AIS, provider, false, dataProvider.getVersion());

        // TODO C4PO-8978 introduced this workaround because for some reason, some banks (i.e. RBSG, Nationwide) add the "/" (slash sign)
        // to the Keebo's redirect_uri which leads ot HTTP-400 during auth_code exchange
        String redirectUrlPostedBackFromSite = apiCreateAccessMeansDTO.getRedirectUrlPostedBackFromSite();
        if (redirectUrlPostedBackFromSite != null && redirectUrlPostedBackFromSite.startsWith(FAULTY_KEEBO_REDIRECT_URL_PART)) {
            redirectUrlPostedBackFromSite = redirectUrlPostedBackFromSite.replace(FAULTY_KEEBO_REDIRECT_URL_PART, PROPER_KEEBO_REDIRECT_URL_PART);
        }
        UrlCreateAccessMeansRequest urlCreateAccessMeans = new UrlCreateAccessMeansRequest(
                apiCreateAccessMeansDTO.getUserId(),
                redirectUrlPostedBackFromSite,
                apiCreateAccessMeansDTO.getBaseClientRedirectUrl(),
                authenticationMeans,
                decryptProviderState(apiCreateAccessMeansDTO.getProviderState()),
                signer,
                restTemplateManager,
                apiCreateAccessMeansDTO.getFilledInUserSiteFormValues(),
                apiCreateAccessMeansDTO.getState(),
                apiCreateAccessMeansDTO.getPsuIpAddress()
        );

        return circuitBreakerAisService.createNewAccessMeans(clientToken, siteId, provider, redirectUrlId, vaultKeys, dataProvider, urlCreateAccessMeans);
    }

    public void notifyUserSiteDelete(final String provider,
                                     final UUID siteId,
                                     final ApiNotifyUserSiteDeleteDTO apiNotifyUserSiteDeleteDTO,
                                     final ClientToken clientToken,
                                     final boolean forceExperimentalVersion) {
        UUID clientId = clientToken.getClientIdClaim();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));
        UUID redirectUrlId = apiNotifyUserSiteDeleteDTO.getAuthenticationMeansReference().getRedirectUrlId();
        Map<String, BasicAuthenticationMean> authenticationMeans =
                clientAuthenticationMeansService.acquireAuthenticationMeans(provider, AIS, apiNotifyUserSiteDeleteDTO.getAuthenticationMeansReference());
        UrlDataProvider experimentalDataProvider = providerFactoryService.getProvider(provider, UrlDataProvider.class, AIS, EXPERIMENTAL);

        UrlOnUserSiteDeleteRequest experimentalVersionUrlOnUserSiteDeleteRequest = createOnUserSiteDeleteRequest(
                apiNotifyUserSiteDeleteDTO,
                experimentalDataProvider,
                clientToken,
                authenticationMeans);

        if (forceExperimentalVersion) {
            circuitBreakerAisService.notifyUserSiteDelete(
                    clientToken,
                    siteId,
                    provider,
                    redirectUrlId,
                    experimentalVersionUrlOnUserSiteDeleteRequest,
                    experimentalDataProvider
            );
        } else {
            UrlDataProvider stableDataProvider = providerFactoryService.getProvider(provider, UrlDataProvider.class, AIS, STABLE);
            UrlOnUserSiteDeleteRequest stableVersionUrlOnUserSiteDeleteRequest = createOnUserSiteDeleteRequest(
                    apiNotifyUserSiteDeleteDTO,
                    stableDataProvider,
                    clientToken,
                    authenticationMeans);

            circuitBreakerAisService.notifyUserSiteDeleteWithFallbackToStableVersion(
                    clientToken,
                    siteId,
                    provider,
                    redirectUrlId,
                    experimentalVersionUrlOnUserSiteDeleteRequest,
                    stableVersionUrlOnUserSiteDeleteRequest,
                    experimentalDataProvider,
                    stableDataProvider);
        }

        log.info(HAPPY_FLOW_MARKER, "Successfully deleted user site.");
    }

    private UrlOnUserSiteDeleteRequest createOnUserSiteDeleteRequest(ApiNotifyUserSiteDeleteDTO apiNotifyUserSiteDeleteDTO,
                                                                     UrlDataProvider dataProvider,
                                                                     ClientToken clientToken,
                                                                     Map<String, BasicAuthenticationMean> authenticationMeans) {
        JcaSigner signer = jcaSignerFactory.getForClientToken(clientToken);
        RestTemplateManager restTemplateManager = restTemplateManagerCache.getForClientProvider(
                clientToken,
                AIS,
                dataProvider.getProviderIdentifier(),
                false,
                dataProvider.getVersion());

        return new UrlOnUserSiteDeleteRequest(
                apiNotifyUserSiteDeleteDTO.getExternalConsentId(),
                apiNotifyUserSiteDeleteDTO.getAuthenticationMeansReference(),
                authenticationMeans,
                apiNotifyUserSiteDeleteDTO.getAccessMeansDTO(),
                signer,
                restTemplateManager,
                apiNotifyUserSiteDeleteDTO.getPsuIpAddress()
        );
    }

    private Integer getConsentExpirationInDays(String provider) {
        AisSiteDetails aisSiteDetails = siteDetailsService.getAisProviderSitesDataBySiteId().values().stream()
                .map(AisProviderSiteData::getAisSiteDetails)
                .flatMap(List::stream)
                .filter(siteDetails -> provider.equals(siteDetails.getProviderKey()))
                .findAny()
                .orElseThrow(IllegalStateException::new);
        Integer consentExpiration = aisSiteDetails.getConsentExpiryInDays();
        if (ObjectUtils.isEmpty(consentExpiration)) {
            throw new IllegalStateException();
        }
        return consentExpiration;
    }

    private String decryptProviderState(final String encryptedProviderState) {
        if (!StringUtils.hasLength(encryptedProviderState)) {
            return null;
        }
        return AesEncryptionUtil.decrypt(encryptedProviderState, vaultKeys.getEncryptionKey());
    }
}
