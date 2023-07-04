package com.yolt.providers.web.service;

import com.yolt.providers.common.pis.common.GetStatusRequest;
import com.yolt.providers.common.pis.common.PaymentStatusResponseDTO;
import com.yolt.providers.common.pis.common.SubmitPaymentRequest;
import com.yolt.providers.common.pis.common.SubmitPaymentRequestDTO;
import com.yolt.providers.common.pis.ukdomestic.*;
import com.yolt.providers.common.providerinterface.PaymentSubmissionProvider;
import com.yolt.providers.common.providerinterface.UkDomesticPaymentProvider;
import com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService;
import com.yolt.providers.web.controller.dto.*;
import com.yolt.providers.web.cryptography.signing.JcaSignerFactory;
import com.yolt.providers.web.cryptography.transport.MutualTLSRestTemplateManagerCache;
import com.yolt.providers.web.encryption.AesEncryptionUtil;
import com.yolt.providers.web.exception.IncorrectDateException;
import com.yolt.providers.web.service.circuitbreaker.CircuitBreakerSecuredUkDomesticPaymentService;
import com.yolt.providers.web.service.configuration.VersionType;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.UUID;

import static com.yolt.providers.web.service.ProviderService.PROVIDER_MDC_KEY;
import static com.yolt.providers.web.service.ProviderVersioningUtil.getVersionType;
import static nl.ing.lovebird.logging.MDCContextCreator.CLIENT_ID_HEADER_NAME;
import static nl.ing.lovebird.providerdomain.ServiceType.PIS;

@Service
public class ProviderUkDomesticPaymentService {

    private final ProviderFactoryService providerFactoryService;
    private final ClientAuthenticationMeansService clientAuthenticationMeansService;
    private final JcaSignerFactory jcaSignerFactory;
    private final MutualTLSRestTemplateManagerCache restTemplateManagerCache;
    private final ProviderVaultKeys vaultKeys;
    private final CircuitBreakerSecuredUkDomesticPaymentService circuitBreakerSecuredUkDomesticPaymentService;
    private final Clock clock;

    public ProviderUkDomesticPaymentService(ProviderFactoryService providerFactoryService,
                                            ClientAuthenticationMeansService clientAuthenticationMeansService,
                                            JcaSignerFactory jcaSignerFactory,
                                            MutualTLSRestTemplateManagerCache restTemplateManagerCache,
                                            ProviderVaultKeys vaultKeys,
                                            CircuitBreakerSecuredUkDomesticPaymentService circuitBreakerSecuredUkDomesticPaymentService,
                                            Clock clock) {
        this.providerFactoryService = providerFactoryService;
        this.clientAuthenticationMeansService = clientAuthenticationMeansService;
        this.jcaSignerFactory = jcaSignerFactory;
        this.restTemplateManagerCache = restTemplateManagerCache;
        this.vaultKeys = vaultKeys;
        this.circuitBreakerSecuredUkDomesticPaymentService = circuitBreakerSecuredUkDomesticPaymentService;
        this.clock = clock;
    }

    public ExternalInitiateUkDomesticPaymentResponseDTO initiateSinglePayment(final String provider,
                                                                              final ExternalInitiateUkScheduledPaymentRequestDTO requestDTO,
                                                                              final ClientToken clientToken,
                                                                              final UUID siteId,
                                                                              final boolean forceExperimentalVersion) {
        UUID clientId = clientToken.getClientIdClaim();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));
        VersionType versionType = getVersionType(forceExperimentalVersion);
        final UkDomesticPaymentProvider ukPaymentProvider = providerFactoryService.getProvider(provider, UkDomesticPaymentProvider.class, PIS, versionType);

        AuthenticationMeansReference authenticationMeansReference = requestDTO.getAuthenticationMeansReference();
        InitiateUkDomesticPaymentResponseDTO unencryptedResponse;
        LocalDate executionDate = requestDTO.getRequestDTO().getExecutionDate();
        if (executionDate == null) {
            unencryptedResponse = initiateUkDomesticSinglePayment(siteId, provider, requestDTO, clientToken, ukPaymentProvider, authenticationMeansReference);
        } else {
            validateExecutionDate(executionDate);
            unencryptedResponse = initiateUkDomesticScheduledPayment(siteId, provider, requestDTO, clientToken, ukPaymentProvider, authenticationMeansReference);
        }
        return new ExternalInitiateUkDomesticPaymentResponseDTO(unencryptedResponse.getLoginUrl(),
                encryptProviderState(unencryptedResponse.getProviderState()),
                unencryptedResponse.getPaymentExecutionContextMetadata());
    }

    private void validateExecutionDate(final LocalDate executionDate) {
        if (executionDate.isBefore(LocalDate.now(clock).plusDays(1))) {
            throw new IncorrectDateException("Execution date should not be today");
        }
    }

    public PaymentStatusResponseDTO submitSinglePayment(final String provider,
                                                        final SubmitPaymentRequestDTO requestDTO,
                                                        final ClientToken clientToken,
                                                        final UUID siteId,
                                                        final boolean forceExperimentalVersion) {
        UUID clientId = clientToken.getClientIdClaim();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));
        VersionType versionType = getVersionType(forceExperimentalVersion);
        final PaymentSubmissionProvider ukPaymentProvider = providerFactoryService.getProvider(provider, PaymentSubmissionProvider.class, PIS, versionType);

        AuthenticationMeansReference authenticationMeansReference = requestDTO.getAuthenticationMeansReference();
        final SubmitPaymentRequest submitPaymentRequest = new SubmitPaymentRequest(
                decryptProviderState(requestDTO.getProviderState()),
                clientAuthenticationMeansService.acquireAuthenticationMeans(provider, PIS, authenticationMeansReference),
                requestDTO.getRedirectUrlPostedBackFromSite(),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, provider, false, ukPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                authenticationMeansReference);

        var result = circuitBreakerSecuredUkDomesticPaymentService.submitSinglePayment(
                clientToken,
                siteId,
                provider,
                authenticationMeansReference.getRedirectUrlId(),
                ukPaymentProvider,
                submitPaymentRequest);
        String providerState = encryptProviderState(result.getProviderState());
        return new PaymentStatusResponseDTO(providerState, result.getPaymentId(), result.getPaymentExecutionContextMetadata());
    }

    public PaymentStatusResponseDTO submitPeriodicPayment(final String provider,
                                                          final SubmitPaymentRequestDTO requestDTO,
                                                          final ClientToken clientToken,
                                                          final UUID siteId,
                                                          final boolean forceExperimentalVersion) {
        UUID clientId = clientToken.getClientIdClaim();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));
        VersionType versionType = getVersionType(forceExperimentalVersion);
        final PaymentSubmissionProvider ukPaymentProvider = providerFactoryService.getProvider(provider, PaymentSubmissionProvider.class, PIS, versionType);

        AuthenticationMeansReference authenticationMeansReference = requestDTO.getAuthenticationMeansReference();
        final SubmitPaymentRequest submitPaymentRequest = new SubmitPaymentRequest(
                decryptProviderState(requestDTO.getProviderState()),
                clientAuthenticationMeansService.acquireAuthenticationMeans(provider, PIS, authenticationMeansReference),
                requestDTO.getRedirectUrlPostedBackFromSite(),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, provider, false, ukPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                authenticationMeansReference);

        var result = circuitBreakerSecuredUkDomesticPaymentService.submitPeriodicPayment(
                clientToken,
                siteId,
                provider,
                authenticationMeansReference.getRedirectUrlId(),
                ukPaymentProvider,
                submitPaymentRequest);
        String providerState = encryptProviderState(result.getProviderState());
        return new PaymentStatusResponseDTO(providerState, result.getPaymentId(), result.getPaymentExecutionContextMetadata());
    }

    public ExternalInitiateUkDomesticPaymentResponseDTO initiatePeriodicPayment(final String provider,
                                                                                final InitiateUkPeriodicPaymentRequestDTO requestDTO,
                                                                                final ClientToken clientToken,
                                                                                final UUID siteId,
                                                                                final boolean forceExperimentalVersion) {
        UUID clientId = clientToken.getClientIdClaim();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));
        VersionType versionType = getVersionType(forceExperimentalVersion);
        final UkDomesticPaymentProvider ukPaymentProvider = providerFactoryService.getProvider(provider, UkDomesticPaymentProvider.class, PIS, versionType);

        AuthenticationMeansReference authenticationMeansReference = requestDTO.getAuthenticationMeansReference();
        InitiateUkDomesticPaymentResponseDTO unencryptedResponse;

        unencryptedResponse = initiateUkDomesticPeriodicPayment(provider, siteId, requestDTO, clientToken, ukPaymentProvider, authenticationMeansReference);
        return new ExternalInitiateUkDomesticPaymentResponseDTO(unencryptedResponse.getLoginUrl(),
                encryptProviderState(unencryptedResponse.getProviderState()),
                unencryptedResponse.getPaymentExecutionContextMetadata());
    }

    public PaymentStatusResponseDTO getPaymentStatus(final String provider,
                                                     final GetPaymentStatusRequestDTO requestDTO,
                                                     final ClientToken clientToken,
                                                     final UUID siteId,
                                                     final boolean forceExperimentalVersion) {
        UUID clientId = clientToken.getClientIdClaim();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));
        VersionType versionType = getVersionType(forceExperimentalVersion);
        final PaymentSubmissionProvider ukPaymentProvider = providerFactoryService.getProvider(provider, PaymentSubmissionProvider.class, PIS, versionType);

        AuthenticationMeansReference authenticationMeansReference = requestDTO.getAuthenticationMeansReference();
        final GetStatusRequest getStatusRequest = new GetStatusRequest(
                decryptProviderState(requestDTO.getProviderState()),
                requestDTO.getPaymentId(),
                clientAuthenticationMeansService.acquireAuthenticationMeans(provider, PIS, authenticationMeansReference),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, provider, false, ukPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                authenticationMeansReference);

        var result = circuitBreakerSecuredUkDomesticPaymentService.getPaymentStatus(
                clientToken,
                siteId,
                provider,
                authenticationMeansReference.getRedirectUrlId(),
                ukPaymentProvider,
                getStatusRequest);
        String providerState = encryptProviderState(result.getProviderState());
        return new PaymentStatusResponseDTO(providerState, result.getPaymentId(), result.getPaymentExecutionContextMetadata());
    }

    private InitiateUkDomesticPaymentResponseDTO initiateUkDomesticSinglePayment(final UUID siteId, final String provider, final ExternalInitiateUkScheduledPaymentRequestDTO requestDTO, final ClientToken clientToken, final UkDomesticPaymentProvider ukPaymentProvider, final AuthenticationMeansReference authenticationMeansReference) {
        final InitiateUkDomesticPaymentRequest initiatePaymentRequest = new InitiateUkDomesticPaymentRequest(
                requestDTO.getRequestDTO(),
                requestDTO.getBaseClientRedirectUrl(),
                requestDTO.getState(),
                clientAuthenticationMeansService.acquireAuthenticationMeans(provider, PIS, authenticationMeansReference),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, provider, false, ukPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                authenticationMeansReference
        );
        return circuitBreakerSecuredUkDomesticPaymentService.initiateSinglePayment(
                clientToken,
                siteId,
                provider,
                authenticationMeansReference.getRedirectUrlId(),
                ukPaymentProvider,
                initiatePaymentRequest);
    }

    private InitiateUkDomesticPaymentResponseDTO initiateUkDomesticScheduledPayment(final UUID siteId, final String provider, final ExternalInitiateUkScheduledPaymentRequestDTO requestDTO, final ClientToken clientToken, final UkDomesticPaymentProvider ukPaymentProvider, final AuthenticationMeansReference authenticationMeansReference) {
        final InitiateUkDomesticScheduledPaymentRequest initiateScheduledPaymentRequest = new InitiateUkDomesticScheduledPaymentRequest(
                prepareRequestDTO(requestDTO.getRequestDTO()),
                requestDTO.getBaseClientRedirectUrl(),
                requestDTO.getState(),
                clientAuthenticationMeansService.acquireAuthenticationMeans(provider, PIS, authenticationMeansReference),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, provider, false, ukPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                authenticationMeansReference
        );
        return circuitBreakerSecuredUkDomesticPaymentService.initiateScheduledPayment(clientToken,
                siteId,
                provider,
                authenticationMeansReference.getRedirectUrlId(),
                ukPaymentProvider,
                initiateScheduledPaymentRequest);
    }

    private InitiateUkDomesticScheduledPaymentRequestDTO prepareRequestDTO(final ExternalInitiateUkDomesticScheduledPaymentRequestDTO requestDTO) {
        OffsetDateTime executionDate = requestDTO.getExecutionDate() != null ? OffsetDateTime.of(requestDTO.getExecutionDate(), LocalTime.of(8, 0), ZoneOffset.UTC) : null;
        return new InitiateUkDomesticScheduledPaymentRequestDTO(
                requestDTO.getEndToEndIdentification(),
                requestDTO.getCurrencyCode(),
                requestDTO.getAmount(),
                requestDTO.getCreditorAccount(),
                requestDTO.getDebtorAccount(),
                requestDTO.getRemittanceInformationUnstructured(),
                requestDTO.getDynamicFields(),
                executionDate
        );
    }

    private InitiateUkDomesticPaymentResponseDTO initiateUkDomesticPeriodicPayment(final String provider,
                                                                                   final UUID siteId,
                                                                                   final InitiateUkPeriodicPaymentRequestDTO requestDTO,
                                                                                   final ClientToken clientToken,
                                                                                   final UkDomesticPaymentProvider ukPaymentProvider,
                                                                                   final AuthenticationMeansReference authenticationMeansReference) {
        final InitiateUkDomesticPeriodicPaymentRequest initiatePeriodicPaymentRequest = new InitiateUkDomesticPeriodicPaymentRequest(
                requestDTO.getRequestDTO(),
                requestDTO.getBaseClientRedirectUrl(),
                requestDTO.getState(),
                clientAuthenticationMeansService.acquireAuthenticationMeans(provider, PIS, authenticationMeansReference),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, provider, false, ukPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                authenticationMeansReference
        );
        return circuitBreakerSecuredUkDomesticPaymentService.initiatePeriodicPayment(
                clientToken,
                siteId,
                provider,
                authenticationMeansReference.getRedirectUrlId(),
                ukPaymentProvider,
                initiatePeriodicPaymentRequest
        );
    }

    private String encryptProviderState(final String providerState) {
        String encryptedValue = providerState == null ? "" : providerState;
        return AesEncryptionUtil.encrypt(encryptedValue, vaultKeys.getEncryptionKey());
    }

    private String decryptProviderState(final String providerState) {
        return StringUtils.isEmpty(providerState) ? providerState : AesEncryptionUtil.decrypt(providerState, vaultKeys.getEncryptionKey());
    }
}
