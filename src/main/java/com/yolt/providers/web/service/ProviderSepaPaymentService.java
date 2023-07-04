package com.yolt.providers.web.service;

import com.yolt.providers.common.pis.sepa.*;
import com.yolt.providers.common.providerinterface.SepaPaymentProvider;
import com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService;
import com.yolt.providers.web.controller.dto.*;
import com.yolt.providers.web.cryptography.signing.JcaSignerFactory;
import com.yolt.providers.web.cryptography.transport.MutualTLSRestTemplateManagerCache;
import com.yolt.providers.web.encryption.AesEncryptionUtil;
import com.yolt.providers.web.exception.IncorrectDateException;
import com.yolt.providers.web.service.circuitbreaker.CircuitBreakerSecuredSepaPaymentService;
import com.yolt.providers.web.service.configuration.VersionType;
import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.clienttokens.ClientToken;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static com.yolt.providers.web.service.ProviderService.PROVIDER_MDC_KEY;
import static com.yolt.providers.web.service.ProviderVersioningUtil.getVersionType;
import static nl.ing.lovebird.logging.MDCContextCreator.CLIENT_ID_HEADER_NAME;
import static nl.ing.lovebird.providerdomain.ServiceType.PIS;

@Service
@RequiredArgsConstructor
public class ProviderSepaPaymentService {

    private final ProviderFactoryService providerFactoryService;
    private final ClientAuthenticationMeansService clientAuthenticationMeansService;
    private final JcaSignerFactory jcaSignerFactory;
    private final MutualTLSRestTemplateManagerCache restTemplateManagerCache;
    private final CircuitBreakerSecuredSepaPaymentService circuitBreakerSecuredSepaPaymentService;
    private final ProviderVaultKeys vaultKeys;
    private final Clock clock;

    public ExternalLoginUrlAndStateDTO initiateSinglePayment(final String provider,
                                                             final ExternalInitiateSepaPaymentRequestDTO requestDTO,
                                                             final ClientToken clientToken,
                                                             final UUID siteId,
                                                             final boolean forceExperimentalVersion) {
        UUID clientId = clientToken.getClientIdClaim();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        VersionType versionType = getVersionType(forceExperimentalVersion);
        final SepaPaymentProvider sepaPaymentProvider = providerFactoryService.getProvider(provider, SepaPaymentProvider.class, PIS, versionType);
        final InitiatePaymentRequest initiatePaymentRequest = new InitiatePaymentRequest(
                prepareRequestDTO(requestDTO.getRequestDTO()),
                requestDTO.getBaseClientRedirectUrl(),
                requestDTO.getState(),
                clientAuthenticationMeansService.acquireAuthenticationMeans(provider, PIS, requestDTO.getAuthenticationMeansReference()),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, provider, false, sepaPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                requestDTO.getAuthenticationMeansReference()
        );
        UUID redirectUrlId = requestDTO.getAuthenticationMeansReference().getRedirectUrlId();
        LoginUrlAndStateDTO loginUrlAndState;
        LocalDate executionDate = requestDTO.getRequestDTO().getExecutionDate();
        if (executionDate == null) {
            loginUrlAndState = circuitBreakerSecuredSepaPaymentService.initiateSinglePayment(clientToken, siteId, provider, redirectUrlId,
                    sepaPaymentProvider, initiatePaymentRequest);
        } else {
            validateExecutionDate(executionDate);
            loginUrlAndState = circuitBreakerSecuredSepaPaymentService.initiateScheduledPayment(clientToken, siteId, provider, redirectUrlId,
                    sepaPaymentProvider, initiatePaymentRequest);
        }
        return new ExternalLoginUrlAndStateDTO(loginUrlAndState.getLoginUrl(), encryptProviderState(loginUrlAndState.getProviderState()), loginUrlAndState.getPaymentExecutionContextMetadata());
    }

    private void validateExecutionDate(final LocalDate executionDate) {
        if (executionDate.isBefore(LocalDate.now(clock).plusDays(1))) {
            throw new IncorrectDateException("Execution date should not be today");
        }
    }

    @Valid
    @NotNull
    private SepaInitiatePaymentRequestDTO prepareRequestDTO(final @Valid @NotNull ExternalSepaInitiatePaymentRequestDTO requestDTO) {
        return SepaInitiatePaymentRequestDTO.builder()
                .creditorAccount(requestDTO.getCreditorAccount())
                .creditorName(requestDTO.getCreditorName())
                .endToEndIdentification(requestDTO.getEndToEndIdentification())
                .instructedAmount(requestDTO.getInstructedAmount())
                .remittanceInformationUnstructured(requestDTO.getRemittanceInformationUnstructured())
                .debtorAccount(requestDTO.getDebtorAccount())
                .executionDate(requestDTO.getExecutionDate())
                .instructionPriority(requestDTO.getInstructionPriority())
                .dynamicFields(requestDTO.getDynamicFields())
                .build();
    }

    public LoginUrlAndStateDTO initiatePeriodicPayment(final String provider,
                                                       final InitiateSepaPaymentRequestDTO requestDTO,
                                                       final ClientToken clientToken,
                                                       final UUID siteId,
                                                       final boolean forceExperimentalVersion) {
        UUID clientId = clientToken.getClientIdClaim();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        validatePeriodicPaymentRequest(requestDTO);

        VersionType versionType = getVersionType(forceExperimentalVersion);
        final SepaPaymentProvider sepaPaymentProvider = providerFactoryService.getProvider(provider, SepaPaymentProvider.class, PIS, versionType);
        final InitiatePaymentRequest initiatePaymentRequest = new InitiatePaymentRequest(
                requestDTO.getRequestDTO(),
                requestDTO.getBaseClientRedirectUrl(),
                requestDTO.getState(),
                clientAuthenticationMeansService.acquireAuthenticationMeans(provider, PIS, requestDTO.getAuthenticationMeansReference()),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, provider, false, sepaPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                requestDTO.getAuthenticationMeansReference()
        );
        UUID redirectUrlId = requestDTO.getAuthenticationMeansReference().getRedirectUrlId();
        LoginUrlAndStateDTO loginUrlAndState = circuitBreakerSecuredSepaPaymentService.initiatePeriodicPayment(clientToken, siteId, provider, redirectUrlId,
                sepaPaymentProvider, initiatePaymentRequest);

        return new LoginUrlAndStateDTO(loginUrlAndState.getLoginUrl(), encryptProviderState(loginUrlAndState.getProviderState()), loginUrlAndState.getPaymentExecutionContextMetadata());
    }

    public SepaPaymentStatusResponseDTO submitSinglePayment(final String provider,
                                                            final SubmitSepaPaymentRequestDTO requestDTO,
                                                            final ClientToken clientToken,
                                                            final UUID siteId,
                                                            final boolean forceExperimentalVersion) {
        UUID clientId = clientToken.getClientIdClaim();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        VersionType versionType = getVersionType(forceExperimentalVersion);
        final SepaPaymentProvider sepaPaymentProvider = providerFactoryService.getProvider(provider, SepaPaymentProvider.class, PIS, versionType);
        final SubmitPaymentRequest submitPaymentRequestDTO = new SubmitPaymentRequest(
                decryptProviderState(requestDTO.getProviderState()),
                clientAuthenticationMeansService.acquireAuthenticationMeans(provider, PIS, requestDTO.getAuthenticationMeansReference()),
                requestDTO.getRedirectUrlPostedBackFromSite(),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, provider, false, sepaPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                requestDTO.getAuthenticationMeansReference()
        );
        UUID redirectUrlId = requestDTO.getAuthenticationMeansReference().getRedirectUrlId();
        SepaPaymentStatusResponseDTO sepaPaymentStatusResponseDTO = circuitBreakerSecuredSepaPaymentService.submitSinglePayment(clientToken, siteId, provider, redirectUrlId, sepaPaymentProvider, submitPaymentRequestDTO);
        return new SepaPaymentStatusResponseDTO(encryptProviderState(sepaPaymentStatusResponseDTO.getProviderState()),
                sepaPaymentStatusResponseDTO.getPaymentId(),
                sepaPaymentStatusResponseDTO.getPaymentExecutionContextMetadata());
    }

    public SepaPaymentStatusResponseDTO submitPeriodicPayment(final String provider,
                                                              final SubmitSepaPaymentRequestDTO requestDTO,
                                                              final ClientToken clientToken,
                                                              final UUID siteId,
                                                              final boolean forceExperimentalVersion) {
        UUID clientId = clientToken.getClientIdClaim();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        VersionType versionType = getVersionType(forceExperimentalVersion);
        final SepaPaymentProvider sepaPaymentProvider = providerFactoryService.getProvider(provider, SepaPaymentProvider.class, PIS, versionType);
        final SubmitPaymentRequest submitPaymentRequestDTO = new SubmitPaymentRequest(
                decryptProviderState(requestDTO.getProviderState()),
                clientAuthenticationMeansService.acquireAuthenticationMeans(provider, PIS, requestDTO.getAuthenticationMeansReference()),
                requestDTO.getRedirectUrlPostedBackFromSite(),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, provider, false, sepaPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                requestDTO.getAuthenticationMeansReference()
        );
        UUID redirectUrlId = requestDTO.getAuthenticationMeansReference().getRedirectUrlId();
        SepaPaymentStatusResponseDTO sepaPaymentStatusResponseDTO = circuitBreakerSecuredSepaPaymentService.submitPeriodicPayment(clientToken, siteId, provider, redirectUrlId, sepaPaymentProvider, submitPaymentRequestDTO);
        return new SepaPaymentStatusResponseDTO(encryptProviderState(sepaPaymentStatusResponseDTO.getProviderState()),
                sepaPaymentStatusResponseDTO.getPaymentId(),
                sepaPaymentStatusResponseDTO.getPaymentExecutionContextMetadata());
    }

    public SepaPaymentStatusResponseDTO getPaymentStatus(final String provider,
                                                         final GetPaymentStatusRequestDTO requestDTO,
                                                         final ClientToken clientToken,
                                                         final UUID siteId,
                                                         final boolean forceExperimentalVersion) {
        UUID clientId = clientToken.getClientIdClaim();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        VersionType versionType = getVersionType(forceExperimentalVersion);
        final SepaPaymentProvider sepaPaymentProvider = providerFactoryService.getProvider(provider, SepaPaymentProvider.class, PIS, versionType);
        final GetStatusRequest getStatusRequest = new GetStatusRequest(
                decryptProviderState(requestDTO.getProviderState()),
                requestDTO.getPaymentId(),
                clientAuthenticationMeansService.acquireAuthenticationMeans(provider, PIS, requestDTO.getAuthenticationMeansReference()),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, provider, false, sepaPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                requestDTO.getAuthenticationMeansReference()
        );

        UUID redirectUrlId = requestDTO.getAuthenticationMeansReference().getRedirectUrlId();
        SepaPaymentStatusResponseDTO sepaPaymentStatusResponseDTO = circuitBreakerSecuredSepaPaymentService.getPaymentStatus(clientToken, siteId, provider, redirectUrlId, sepaPaymentProvider, getStatusRequest);
        return new SepaPaymentStatusResponseDTO(
                encryptProviderState(sepaPaymentStatusResponseDTO.getProviderState()),
                sepaPaymentStatusResponseDTO.getPaymentId(),
                sepaPaymentStatusResponseDTO.getPaymentExecutionContextMetadata()
        );
    }

    private String encryptProviderState(final String providerState) {
        String encryptedValue = providerState == null ? "" : providerState;
        return AesEncryptionUtil.encrypt(encryptedValue, vaultKeys.getEncryptionKey());
    }

    private String decryptProviderState(final String providerState) {
        return StringUtils.isEmpty(providerState) ? providerState : AesEncryptionUtil.decrypt(providerState, vaultKeys.getEncryptionKey());
    }

    private void validatePeriodicPaymentRequest(InitiateSepaPaymentRequestDTO requestDTO) {
        if (requestDTO.getRequestDTO().getPeriodicPaymentInfo() == null) {
            throw new IncorrectDateException("Periodic payment information is missing.");
        }
    }
}
