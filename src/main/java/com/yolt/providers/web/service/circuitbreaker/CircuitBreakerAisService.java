package com.yolt.providers.web.service.circuitbreaker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.ais.DataProviderResponse;
import com.yolt.providers.common.ais.url.*;
import com.yolt.providers.common.domain.dynamic.AccessMeansOrStepDTO;
import com.yolt.providers.common.domain.dynamic.step.FormStep;
import com.yolt.providers.common.domain.dynamic.step.RedirectStep;
import com.yolt.providers.common.domain.dynamic.step.Step;
import com.yolt.providers.common.exception.*;
import com.yolt.providers.common.providerinterface.UrlDataProvider;
import com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreaker;
import com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreakerException;
import com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreakerFactory;
import com.yolt.providers.web.circuitbreaker.ProvidersNonCircuitBreakingTokenInvalidException;
import com.yolt.providers.web.controller.dto.ApiFetchDataDTO;
import com.yolt.providers.web.controller.dto.ProviderServiceResponseDTO;
import com.yolt.providers.web.encryption.AesEncryptionUtil;
import com.yolt.providers.web.exception.ProviderCircuitBreakerFetchDataException;
import com.yolt.providers.web.service.AccountsFilterService;
import com.yolt.providers.web.service.AccountsPostProcessingService;
import com.yolt.providers.web.service.ProviderVaultKeys;
import com.yolt.providers.web.service.TransactionsDataLimiter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.ClientUserToken;
import nl.ing.lovebird.providershared.AccessMeansDTO;
import nl.ing.lovebird.providershared.ProviderServiceResponseStatus;
import nl.ing.lovebird.providershared.form.ExtendedProviderServiceResponseStatus;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.yolt.providers.common.rest.logging.LoggingInterceptor.REQUEST_RESPONSE_DTO_BINDING_CALL_ID;
import static com.yolt.providers.common.rest.logging.RawDataType.MRDD;
import static com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreakerCommand.*;
import static com.yolt.providers.web.configuration.ApplicationConfiguration.OBJECT_MAPPER;
import static net.logstash.logback.marker.Markers.append;

@Service
@Slf4j
public class CircuitBreakerAisService {

    private final ProvidersCircuitBreakerFactory circuitBreakerFactory;

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private static final Marker HAPPY_FLOW_MARKER = append("happy-flow", "true");
    private static final Marker FAILED_FLOW_MARKER = append("raw-data", "true");

    private static final Pattern NL_IBAN_REGEX = Pattern.compile("/(nl|NL[0-9]{2}[a-zA-Z]{4}[0-9]{10})/");

    private static final String BASIC_FETCHING_ERROR_MESSAGE_FORMAT = "Exception while provider {} is fetching accounts and transactions: {}";

    public CircuitBreakerAisService(ProvidersCircuitBreakerFactory circuitBreakerFactory, Clock clock, @Qualifier(OBJECT_MAPPER) ObjectMapper objectMapper) {
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public Step getLoginInfo(final ClientToken clientToken,
                             final UUID siteId,
                             final String provider,
                             final UUID redirectUrlId,
                             final ProviderVaultKeys vaultKeys,
                             final UrlDataProvider dataProvider,
                             final UrlGetLoginRequest urlGetLogin) {
        Map<String, String> mdcContext = getContextMapOrEmpty();
        ProvidersCircuitBreaker circuitBreaker = circuitBreakerFactory.create(clientToken, siteId, provider, AIS_GET_LOGIN_INFO, redirectUrlId);
        return circuitBreaker.run(() -> {
                    MDC.setContextMap(mdcContext);
                    Step step = stepWithEncryptedProviderState(dataProvider.getLoginInfo(urlGetLogin), vaultKeys);
                    log.info(HAPPY_FLOW_MARKER, "Successfully generated login URL.");
                    return step;
                },
                throwable -> {
                    throw new ProvidersCircuitBreakerException("Get login failed.", throwable);
                }
        );
    }

    public AccessMeansOrStepDTO createNewAccessMeans(final ClientToken clientToken,
                                                     final UUID siteId,
                                                     final String provider,
                                                     final UUID redirectUrlId,
                                                     final ProviderVaultKeys vaultKeys,
                                                     final UrlDataProvider dataProvider,
                                                     final UrlCreateAccessMeansRequest urlCreateAccessMeans) {
        Map<String, String> mdcContext = getContextMapOrEmpty();
        ProvidersCircuitBreaker circuitBreaker = circuitBreakerFactory.create(clientToken, siteId, provider, AIS_CREATE_ACCESS_MEANS, redirectUrlId);
        return circuitBreaker.run(() -> {
                    MDC.setContextMap(mdcContext);

                    AccessMeansOrStepDTO accessMeansOrStep = dataProvider.createNewAccessMeans(urlCreateAccessMeans);
                    AccessMeansOrStepDTO toReturn;
                    if (accessMeansOrStep.getAccessMeans() != null) {
                        toReturn = new AccessMeansOrStepDTO(accessMeansOrStep.getAccessMeans());
                    } else {
                        toReturn = new AccessMeansOrStepDTO(stepWithEncryptedProviderState(accessMeansOrStep.getStep(), vaultKeys));
                    }
                    log.info(HAPPY_FLOW_MARKER, "Successfully created access means.");
                    return toReturn;
                },
                throwable -> {
                    throw new ProvidersCircuitBreakerException("Token invalid, this does not count for circuit-breaking.", throwable);
                }
        );
    }

    public AccessMeansDTO refreshAccessMeans(final ClientToken clientToken,
                                             final UUID siteId,
                                             final String provider,
                                             final UUID redirectUrlId,
                                             final UrlDataProvider dataProvider,
                                             final UrlRefreshAccessMeansRequest urlRefreshAccessMeans,
                                             final Instant consentCreationTime,
                                             final Integer consentExpirationInDays) {

        Map<String, String> mdcContext = getContextMapOrEmpty();
        ProvidersCircuitBreaker circuitBreaker = circuitBreakerFactory.create(clientToken, siteId, provider, AIS_REFRESH_ACCESS_MEANS, redirectUrlId);
        return circuitBreaker.run(() -> {
            boolean isConsentValid = checkConsentValidity(consentCreationTime, consentExpirationInDays);
            try {
                MDC.setContextMap(mdcContext);
                var processingStart = Instant.now(clock);
                if (!isConsentValid) {
                    log.info(provider + " Refreshing access means using expired consent which is not allowed");
                }
                AccessMeansDTO result = dataProvider.refreshAccessMeans(urlRefreshAccessMeans);
                var between = Duration.between(processingStart, Instant.now(clock));
                var happyFlowMarker = append("happy-flow", "true")
                        .and(append("execution-time-ms", between.toMillis()));
                log.info(happyFlowMarker, "Successfully refreshed access means.");
                return result;
            } catch (TokenInvalidException e) {
                if (!isConsentValid) {
                    throw new ProvidersNonCircuitBreakingTokenInvalidException("Tried to refresh access means using expired consent", e);
                }
                if (urlRefreshAccessMeans.getAccessMeans().getExpireTime().before(Date.from(Instant.now(clock)))) {
                    throw new ProvidersNonCircuitBreakingTokenInvalidException("Tried to refresh access means using expired access means", e);
                }
                throw new ProvidersNonCircuitBreakingTokenInvalidException(e);
            }
        });
    }

    private boolean checkConsentValidity(Instant consentCreationTime, Integer consentExpirationInDays) {
        if (ObjectUtils.isNotEmpty(consentCreationTime)) {
            return consentCreationTime.plus(consentExpirationInDays, ChronoUnit.DAYS).isAfter(Instant.now(clock));
        }
        //we are assuming that consent is valid if we don't recived creation time from site-management;
        log.info("Consent creation time received from s-m is null");
        return true;
    }

    public ProviderServiceResponseDTO fetchDataAsync(final ClientUserToken clientUserToken,
                                                     final UUID siteId,
                                                     final String provider,
                                                     final UUID redirectUrlId,
                                                     final UrlDataProvider dataProvider,
                                                     final UrlFetchDataRequest urlFetchData,
                                                     final ApiFetchDataDTO apiFetchDataDTO,
                                                     final AccountsFilterService accountsFilterService,
                                                     final TransactionsDataLimiter transactionsDataLimiter,
                                                     final AccountsPostProcessingService accountsPostProcessingService,
                                                     final UUID providerRequestId) {
        Map<String, String> mdcContext = getContextMapOrEmpty();
        ProvidersCircuitBreaker circuitBreaker = circuitBreakerFactory.create(clientUserToken, siteId, provider, AIS_FETCH_DATA, redirectUrlId);
        return circuitBreaker.run(() -> {
                    try {
                        MDC.setContextMap(mdcContext);
                        MDC.put(REQUEST_RESPONSE_DTO_BINDING_CALL_ID, String.valueOf(UUID.randomUUID()));
                        var processingStart = Instant.now(clock);
                        DataProviderResponse unfilteredResponse = dataProvider.fetchData(urlFetchData);

                        DataProviderResponse filteredResponse = accountsFilterService.filterNormalResponse(provider, unfilteredResponse, apiFetchDataDTO.getUserSiteDataFetchInformation(), clientUserToken.getUserIdClaim());
                        DataProviderResponse limitedResponse = transactionsDataLimiter
                                .limitResponseData(filteredResponse, urlFetchData.getTransactionsFetchStartTime());
                        DataProviderResponse postProcessedResponse = accountsPostProcessingService.postProcessDataProviderResponse(limitedResponse);

                        var between = Duration.between(processingStart, Instant.now(clock));
                        var happyFlowMarker = append("happy-flow", "true")
                                .and(append("execution-time-ms", between.toMillis()));
                        int nrOfAccounts = postProcessedResponse.getAccounts().size();
                        long nrOfTransactions = postProcessedResponse.getAccounts().stream().mapToInt(it -> it.getTransactions().size()).sum();
                        log.info(happyFlowMarker, "Successfully fetched data. {} accounts, {} transactions", nrOfAccounts, nrOfTransactions);

                        ProviderServiceResponseDTO providerServiceResponseDTO = new ProviderServiceResponseDTO(
                                postProcessedResponse.getAccounts(),
                                ProviderServiceResponseStatus.FINISHED,
                                apiFetchDataDTO.getProviderRequestId()
                        );

                        logProviderServiceResponseDTOToRdd(providerServiceResponseDTO);
                        return providerServiceResponseDTO;
                    } catch (SiteActionNeededRuntimeException e) { // TODO - Should be align with form providers - see C4PO-1412 for details
                        return ProviderServiceResponseDTO.getEmptyInstance(ExtendedProviderServiceResponseStatus.SITE_ACTION_NEEDED, apiFetchDataDTO.getProviderRequestId());
                    } catch (TokenInvalidException e) {
                        return ProviderServiceResponseDTO
                                .getEmptyInstance(ProviderServiceResponseStatus.TOKEN_INVALID,
                                        apiFetchDataDTO.getProviderRequestId());
                    } catch (BackPressureRequestException e) {
                        return ProviderServiceResponseDTO
                                .getEmptyInstance(ProviderServiceResponseStatus.BACK_PRESSURE_REQUEST,
                                        apiFetchDataDTO.getProviderRequestId());
                    } catch (ProviderFetchDataException providerFetchDataException) {
                        throw new ProviderCircuitBreakerFetchDataException(providerFetchDataException);
                    }
                },
                throwable -> {
                    Throwable exception = throwable.getCause() != null ? throwable.getCause() : throwable;
                    log.warn(BASIC_FETCHING_ERROR_MESSAGE_FORMAT, provider, getExceptionMessage(exception), new MessageSuppressingException(exception));

                    ProviderServiceResponseDTO providerServiceResponseDTO = ProviderServiceResponseDTO
                            .getEmptyInstance(ProviderServiceResponseStatus.UNKNOWN_ERROR, providerRequestId);

                    logProviderServiceResponseDTOToRdd(providerServiceResponseDTO);
                    return providerServiceResponseDTO;
                });
    }

    private void logProviderServiceResponseDTOToRdd(final ProviderServiceResponseDTO providerServiceResponseDTO) {
        Marker responseMarker = append("raw-data", "true")
                .and(append("raw-data-type", MRDD))
                .and(append("raw-data-call-id", MDC.get(REQUEST_RESPONSE_DTO_BINDING_CALL_ID))); //NOSHERIFF as indicated in C4PO-9255 (medium RDD) this should be used here

        try {
            log.debug(responseMarker, " ProviderServiceResponseDTO: " + objectMapper.writeValueAsString(providerServiceResponseDTO));
        } catch (JsonProcessingException e) {
            log.debug(responseMarker, "Cannot write json response due to error: " + e.getMessage() + " ProviderServiceResponseDTO: " + providerServiceResponseDTO.toString());
        }
    }

    private String getExceptionMessage(Throwable exception) {
        if (exception == null || exception.getMessage() == null) {
            return "null";
        }
        Matcher matcher = NL_IBAN_REGEX.matcher(exception.getMessage());
        if (!matcher.find()) {
            return exception.getMessage();
        }
        log.debug(FAILED_FLOW_MARKER, exception.getMessage());
        return exception.getMessage().replaceAll(matcher.group(1), "{check IBAN in RDD}");
    }

    public void notifyUserSiteDeleteWithFallbackToStableVersion(final ClientToken clientToken,
                                                                final UUID siteId,
                                                                final String provider,
                                                                final UUID redirectUrlId,
                                                                final UrlOnUserSiteDeleteRequest urlOnUserSiteDeleteRequest,
                                                                final UrlOnUserSiteDeleteRequest fallbackUrlOnUserSiteDeleteRequest,
                                                                final UrlDataProvider dataProvider,
                                                                final UrlDataProvider fallbackDataProvider) {
        Map<String, String> mdcContext = getContextMapOrEmpty();
        ProvidersCircuitBreaker circuitBreaker = circuitBreakerFactory.create(clientToken, siteId, provider, AIS_NOTIFY_USER_SITE_DELETE, redirectUrlId);
        circuitBreaker.run(() -> {
                    try {
                        MDC.setContextMap(mdcContext);
                        dataProvider.onUserSiteDelete(urlOnUserSiteDeleteRequest);
                    } catch (HttpStatusCodeException e) {
                        log.warn("Received status code {} when deleting account request while using experimental ProviderVersion of {} provider. Executing stable version", e.getStatusCode(), provider, e);
                        handleOnUserSiteDelete(fallbackDataProvider, fallbackUrlOnUserSiteDeleteRequest);
                    } catch (RestClientException e) {
                        log.warn("Exception of class {} occurred when deleting account request while using experimental ProviderVersion of {} provider. Executing stable version", e.getClass().getName(), provider, e);
                        handleOnUserSiteDelete(fallbackDataProvider, fallbackUrlOnUserSiteDeleteRequest);
                    } catch (TokenInvalidException e) {
                        log.warn("Problem with Token occurred when deleting account request while using experimental ProviderVersion of {} provider. Executing stable version", provider, e);
                        handleOnUserSiteDelete(fallbackDataProvider, fallbackUrlOnUserSiteDeleteRequest);
                    } catch (Exception e) {
                        log.warn("Problem occurred when deleting account request while using experimental ProviderVersion of {} provider. Executing stable version. {}", provider, e.getMessage(), e);
                        handleOnUserSiteDelete(fallbackDataProvider, fallbackUrlOnUserSiteDeleteRequest);
                    }
                    return null; // Needed because of the generic Void type
                },
                throwable -> {
                    throw new ProvidersCircuitBreakerException("Delete of user site failed.", throwable);
                });
    }

    public void notifyUserSiteDelete(final ClientToken clientToken,
                                     final UUID siteId,
                                     final String provider,
                                     final UUID redirectUrlId,
                                     final UrlOnUserSiteDeleteRequest urlOnUserSiteDeleteRequest,
                                     final UrlDataProvider dataProvider) {
        Map<String, String> mdcContext = getContextMapOrEmpty();
        ProvidersCircuitBreaker circuitBreaker = circuitBreakerFactory.create(clientToken, siteId, provider, AIS_NOTIFY_USER_SITE_DELETE, redirectUrlId);
        circuitBreaker.run(() -> {
                    try {
                        MDC.setContextMap(mdcContext);
                        dataProvider.onUserSiteDelete(urlOnUserSiteDeleteRequest);
                    } catch (HttpStatusCodeException e) {
                        log.warn("Received status code {} when deleting account request for, provider={}", e.getStatusCode(), provider, e);
                    } catch (RestClientException e) {
                        log.warn("Exception of class {} occurred when deleting account request, provider={}", e.getClass().getName(), provider, e);
                    } catch (TokenInvalidException e) {
                        log.warn("Problem with Token occurred when deleting account request, provider={}", provider, e);
                        throw new ProvidersCircuitBreakerException(e);
                    } catch (Exception e) {
                        log.warn("Problem occurred when deleting account request, provider={} message={}", provider, e.getMessage(), e);
                    }
                    return null; // Needed because of the generic Void type
                },
                throwable -> {
                    throw new ProvidersCircuitBreakerException("Delete of user site failed.", throwable);
                });
    }

    private Map<String, String> getContextMapOrEmpty() {
        Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
        return copyOfContextMap != null ? copyOfContextMap : new HashMap<>();
    }

    private Step stepWithEncryptedProviderState(@NonNull final Step step, final ProviderVaultKeys vaultKeys) {
        String encryptedProviderState = step.getProviderState() != null ? AesEncryptionUtil.encrypt(step.getProviderState(), vaultKeys.getEncryptionKey()) : null;
        if (step instanceof FormStep formStep) {
            return new FormStep(formStep.getForm(), formStep.getEncryptionDetails(), formStep.getTimeoutTime(), encryptedProviderState);
        } else if (step instanceof RedirectStep redirectStep) {
            return new RedirectStep(redirectStep.getRedirectUrl(), redirectStep.getExternalConsentId(), encryptedProviderState);
        } else {
            throw new NotImplementedException("not implemented for step of type " + step.getClass());
        }
    }

    private void handleOnUserSiteDelete(UrlDataProvider dataProvider, UrlOnUserSiteDeleteRequest request) {
        try {
            dataProvider.onUserSiteDelete(request);
        } catch (TokenInvalidException e) {
            throw new ProvidersCircuitBreakerException(e);
        }
    }
}
