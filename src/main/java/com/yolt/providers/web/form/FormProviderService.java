package com.yolt.providers.web.form;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.ais.DataProviderResponse;
import com.yolt.providers.common.ais.form.*;
import com.yolt.providers.common.cryptography.RestTemplateManager;
import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import com.yolt.providers.common.exception.*;
import com.yolt.providers.common.providerinterface.FormDataProvider;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.common.providerinterface.extension.CallbackDataProviderExtension;
import com.yolt.providers.common.versioning.ProviderVersion;
import com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService;
import com.yolt.providers.web.controller.dto.FormCreateNewExternalUserSiteDTO;
import com.yolt.providers.web.controller.dto.FormTriggerRefreshAndFetchDataDTO;
import com.yolt.providers.web.controller.dto.FormUpdateExternalUserSiteDTO;
import com.yolt.providers.web.controller.dto.*;
import com.yolt.providers.web.cryptography.signing.JcaSigner;
import com.yolt.providers.web.cryptography.signing.JcaSignerFactory;
import com.yolt.providers.web.cryptography.transport.MutualTLSRestTemplateManagerCache;
import com.yolt.providers.web.form.externalids.ProviderExternalUserIdsSyncKafkaPublisher;
import com.yolt.providers.web.service.*;
import com.yolt.providers.web.service.dto.FetchDataResultDTO;
import com.yolt.providers.web.service.dto.IngestionAccountDTO;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.ClientUserToken;
import nl.ing.lovebird.providershared.AccessMeansDTO;
import nl.ing.lovebird.providershared.DataProviderResponseFailedAccount;
import nl.ing.lovebird.providershared.ProviderServiceResponseStatus;
import nl.ing.lovebird.providershared.ProviderServiceResponseStatusValue;
import nl.ing.lovebird.providershared.callback.CallbackResponseDTO;
import nl.ing.lovebird.providershared.callback.UserDataCallbackResponse;
import nl.ing.lovebird.providershared.callback.UserSiteData;
import nl.ing.lovebird.providershared.form.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.yolt.providers.web.configuration.ApplicationConfiguration.*;
import static com.yolt.providers.web.service.configuration.VersionType.STABLE;
import static nl.ing.lovebird.logging.MDCContextCreator.CLIENT_ID_HEADER_NAME;
import static nl.ing.lovebird.providerdomain.ServiceType.AIS;

@Slf4j
@Service
class FormProviderService {

    /**
     * Sometimes, we spontaneously get data from a scraper. They have a background refresh that pushes data to us.
     * In this situation, no 'ativity' has been created yet, although it's a required field everywhere.
     * <p>
     * We use this field *ONLY* to let accounts-and-transactions + health know "there is new data, but without activity"
     * Those services can then cope with new data without an actually active activity.
     */
    private static final UUID CALLBACK_NO_ACTIVITY_ACTIVITY_ID = new UUID(0, 0);

    private static final String PROVIDER_MDC_KEY = "provider";

    private final ProviderFactoryService providerFactoryService;
    private final TransactionsDataLimiter transactionsDataLimiter;
    private final ProviderServiceResponseProducer providerServiceResponseProducer;
    private final ErrorTopicProducer errorTopicProducer;
    private final ProviderExternalUserIdsSyncKafkaPublisher externalUserIdsPublisher;
    private final Integer externalUserIdsSliceLimit;
    private final SiteManagementClient siteManagementClient;
    private final ClientAuthenticationMeansService clientAuthenticationMeansService;
    private final JcaSignerFactory jcaSignerFactory;
    private final MutualTLSRestTemplateManagerCache restTemplateManagerCache;
    private final AccountsFilterService accountsFilterService;
    private final AccountsPostProcessingService accountsPostProcessingService;
    private final ObjectMapper objectMapper;
    private final AccountsProducer accountsProducer;

    @Autowired
    public FormProviderService(ProviderFactoryService providerFactoryService,  //NOSONAR
                               TransactionsDataLimiter transactionsDataLimiter,
                               ProviderServiceResponseProducer providerServiceResponseProducer,
                               ErrorTopicProducer errorTopicProducer,
                               ProviderExternalUserIdsSyncKafkaPublisher externalUserIdsPublisher,
                               @Value("${lovebird.formProvider.externalUserIdsSliceLimit}") Integer externalUserIdsSliceLimit,
                               SiteManagementClient siteManagementClient,
                               ClientAuthenticationMeansService clientAuthenticationMeansService,
                               JcaSignerFactory jcaSignerFactory,
                               MutualTLSRestTemplateManagerCache restTemplateManagerCache,
                               AccountsFilterService accountsFilterService,
                               AccountsPostProcessingService accountsPostProcessingService,
                               @Qualifier(OBJECT_MAPPER) ObjectMapper objectMapper,
                               AccountsProducer accountsProducer) {
        this.providerFactoryService = providerFactoryService;
        this.transactionsDataLimiter = transactionsDataLimiter;
        this.providerServiceResponseProducer = providerServiceResponseProducer;
        this.errorTopicProducer = errorTopicProducer;
        this.externalUserIdsPublisher = externalUserIdsPublisher;
        this.externalUserIdsSliceLimit = externalUserIdsSliceLimit;
        this.siteManagementClient = siteManagementClient;
        this.clientAuthenticationMeansService = clientAuthenticationMeansService;
        this.jcaSignerFactory = jcaSignerFactory;
        this.restTemplateManagerCache = restTemplateManagerCache;
        this.accountsFilterService = accountsFilterService;
        this.accountsPostProcessingService = accountsPostProcessingService;
        this.objectMapper = objectMapper;
        this.accountsProducer = accountsProducer;
    }

    /**
     * @throws ExternalUserSiteDoesNotExistException This doesn't make sense because for form-providres the accessmeans are not tight to a
     *                                               user-site. It should actually be 'externalUserDoesNotExist'.. But for now we leave it like that because it's too much changes.
     */
    AccessMeansDTO refreshAccessMeansForForm(String provider, ClientToken clientToken, FormRefreshAccessMeansDTO formRefreshAccessMeansDTO) throws ExternalUserSiteDoesNotExistException {
        UUID clientId = formRefreshAccessMeansDTO.getClientId();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        FormDataProvider dataProvider = providerFactoryService.getProvider(provider, FormDataProvider.class, AIS, STABLE);

        FormRefreshAccessMeansRequest formRefreshAccessMeansRequest = new FormRefreshAccessMeansRequest(formRefreshAccessMeansDTO.getAccessMeansDTO(),
                authenticationDetails(clientId, clientToken, provider, dataProvider.getVersion()));

        return dataProvider.refreshAccessMeans(formRefreshAccessMeansRequest);
    }

    void deleteUserSite(String provider, ClientToken clientToken, FormDeleteUserSiteDTO formDeleteUserSite) throws AccessMeansExpiredException {
        UUID clientId = formDeleteUserSite.getClientId();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        FormDataProvider dataProvider = providerFactoryService.getProvider(provider, FormDataProvider.class, AIS, STABLE);
        FormDeleteUserSiteRequest formDeleteUserSiteRequest = new FormDeleteUserSiteRequest(formDeleteUserSite.getAccessMeans(),
                formDeleteUserSite.getUserSiteExternalId(), formDeleteUserSite.getUserId(), authenticationDetails(clientId, clientToken, provider, dataProvider.getVersion()));
        dataProvider.deleteUserSite(formDeleteUserSiteRequest);
    }

    void deleteUser(String provider, ClientToken clientToken, FormDeleteUser formDeleteUser) throws AccessMeansExpiredException {
        UUID clientId = formDeleteUser.getClientId();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        FormDataProvider dataProvider = providerFactoryService.getProvider(provider, FormDataProvider.class, AIS, STABLE);

        FormDeleteUserRequest formDeleteUserRequest = new FormDeleteUserRequest(formDeleteUser.getAccessMeansDTO(), authenticationDetails(clientId, clientToken, provider, dataProvider.getVersion()));
        dataProvider.deleteUser(formDeleteUserRequest);
    }

    LoginFormResponse fetchLoginForm(String provider, ClientToken clientToken, FormFetchLoginDTO formFetchLoginDTO) throws IOException {
        UUID clientId = formFetchLoginDTO.getClientId();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        FormDataProvider dataProvider = providerFactoryService.getProvider(provider, FormDataProvider.class, AIS, STABLE);

        FormFetchLoginFormRequest formFetchLoginFormRequest = new FormFetchLoginFormRequest(formFetchLoginDTO.getExternalSiteId(), authenticationDetails(clientId, clientToken, provider, dataProvider.getVersion()));
        return dataProvider.fetchLoginForm(formFetchLoginFormRequest);
    }

    EncryptionDetails getEncryptionDetails(String provider, ClientToken clientToken, FormGetEncryptionDetailsDTO formGetEncryptionDetailsDTO) {
        FormDataProvider dataProvider = providerFactoryService.getProvider(provider, FormDataProvider.class, AIS, STABLE);

        return dataProvider.getEncryptionDetails(authenticationDetails(formGetEncryptionDetailsDTO.getClientId(), clientToken, provider, dataProvider.getVersion()));
    }

    FormCreateNewUserResponse createNewUser(String provider, ClientToken clientToken, FormCreateNewUserRequestDTO formCreateNewUserRequestDTO) {
        UUID clientId = formCreateNewUserRequestDTO.getClientId();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        FormDataProvider dataProvider = providerFactoryService.getProvider(provider, FormDataProvider.class, AIS, STABLE);
        FormCreateNewUserRequest formCreateNewUserRequest = new FormCreateNewUserRequest(formCreateNewUserRequestDTO.getUserId(),
                formCreateNewUserRequestDTO.getSiteId(),
                formCreateNewUserRequestDTO.isTestUser(),
                authenticationDetails(clientId, clientToken, provider, dataProvider.getVersion()));
        return dataProvider.createNewUser(formCreateNewUserRequest);
    }

    @Async(ASYNC_PROVIDER_FETCH_DATA_EXECUTOR)
    public void updateExternalUserSite(String provider, FormUpdateExternalUserSiteDTO updateExternalUserSiteDTO, ClientUserToken clientUserToken) {
        UUID clientId = updateExternalUserSiteDTO.getClientId();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        FormDataProvider dataProvider = providerFactoryService.getProvider(provider, FormDataProvider.class, AIS, STABLE);
        UUID providerRequestId = updateExternalUserSiteDTO.getProviderRequestId();

        FormUpdateExternalUserSiteRequest formUpdateExternalUserSiteRequest = new FormUpdateExternalUserSiteRequest(updateExternalUserSiteDTO.getExternalSiteId(),
                updateExternalUserSiteDTO.getFormUserSite(),
                updateExternalUserSiteDTO.getAccessMeans(),
                updateExternalUserSiteDTO.getTransactionsFetchStartTime(),
                updateExternalUserSiteDTO.getFilledInUserSiteFormValues(),
                updateExternalUserSiteDTO.getFormSiteLoginForm(),
                loginSucceededDTO -> setStatusToLoginSucceeded(loginSucceededDTO, clientUserToken),
                authenticationDetails(clientId, clientUserToken, provider, dataProvider.getVersion()));
        try {
            DataProviderResponse unfilteredResponse = dataProvider.updateExternalUserSite(formUpdateExternalUserSiteRequest);
            final UserSiteDataFetchInformation userSiteDataFetchInformation = updateExternalUserSiteDTO.getUserSiteDataFetchInformation();
            DataProviderResponse filteredResponse = accountsFilterService.filterNormalResponse(provider, unfilteredResponse, userSiteDataFetchInformation, clientUserToken.getUserIdClaim());
            DataProviderResponse postProcessedResponse = accountsPostProcessingService.postProcessDataProviderResponse(filteredResponse);
            sendProviderServiceResponse(providerRequestId, postProcessedResponse, clientUserToken,
                    formUpdateExternalUserSiteRequest.getTransactionsFetchStartTime(), userSiteDataFetchInformation.getUserSiteId(),
                    updateExternalUserSiteDTO.getActivityId(), provider, updateExternalUserSiteDTO.getSiteId());
        } catch (HandledProviderCheckedException e) {
            sendErrorResponse(e, providerRequestId, clientUserToken);
        } catch (ProviderFetchDataException e) {
            sendFailedProviderServiceResponse(providerRequestId, clientUserToken);
        } catch (RestClientException e) {
            handleRestClientException(e, providerRequestId, clientUserToken, "RestClientException while updating the user site on a scraping provider.");
        } catch (RuntimeException e) {
            log.error("Unknown error while updating the user site on a scraping provider.", e);
            sendFailedProviderServiceResponse(providerRequestId, clientUserToken);
        }
    }

    @Async(ASYNC_PROVIDER_FETCH_DATA_EXECUTOR)
    public void createNewExternalUserSite(String provider, FormCreateNewExternalUserSiteDTO formCreateNewExternalUserSiteDTO, ClientUserToken clientUserToken) {
        UUID clientId = formCreateNewExternalUserSiteDTO.getClientId();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        FormDataProvider dataProvider = providerFactoryService.getProvider(provider, FormDataProvider.class, AIS, STABLE);
        UUID providerRequestId = formCreateNewExternalUserSiteDTO.getProviderRequestId();
        FormCreateNewExternalUserSiteRequest formCreateNewExternalUserSiteRequest = new FormCreateNewExternalUserSiteRequest(
                formCreateNewExternalUserSiteDTO.getExternalSiteId(),
                formCreateNewExternalUserSiteDTO.getUserId(),
                formCreateNewExternalUserSiteDTO.getUserSiteId(),
                formCreateNewExternalUserSiteDTO.getAccessMeans(),
                formCreateNewExternalUserSiteDTO.getTransactionsFetchStartTime(),
                formCreateNewExternalUserSiteDTO.getFilledInUserSiteFormValues(),
                formCreateNewExternalUserSiteDTO.getFormSiteLoginForm(),
                loginSucceededDTO -> setStatusToLoginSucceeded(loginSucceededDTO, clientUserToken),
                this::setNewExternalId,
                authenticationDetails(clientId, clientUserToken, provider, dataProvider.getVersion()));

        try {
            DataProviderResponse unfilteredResponse = dataProvider.createNewExternalUserSite(formCreateNewExternalUserSiteRequest);
            final UserSiteDataFetchInformation userSiteDataFetchInformation = formCreateNewExternalUserSiteDTO.getUserSiteDataFetchInformation();
            DataProviderResponse filteredResponse = accountsFilterService.filterNormalResponse(provider, unfilteredResponse, userSiteDataFetchInformation, clientUserToken.getUserIdClaim());
            DataProviderResponse postProcessedResponse = accountsPostProcessingService.postProcessDataProviderResponse(filteredResponse);
            sendProviderServiceResponse(providerRequestId, postProcessedResponse, clientUserToken,
                    formCreateNewExternalUserSiteRequest.getTransactionsFetchStartTime(), userSiteDataFetchInformation.getUserSiteId(),
                    formCreateNewExternalUserSiteDTO.getActivityId(), provider, formCreateNewExternalUserSiteDTO.getSiteId());
        } catch (HandledProviderCheckedException e) {
            sendErrorResponse(e, providerRequestId, clientUserToken);
        } catch (ProviderFetchDataException e) {
            sendFailedProviderServiceResponse(providerRequestId, clientUserToken);
        } catch (RestClientException e) {
            handleRestClientException(e, providerRequestId, clientUserToken, "RestClientException while creating a new user site on a scraping provider.");
        } catch (RuntimeException e) {
            log.error("Unknown error while creating a new user site on a scraping provider.", e);
            sendFailedProviderServiceResponse(providerRequestId, clientUserToken);
        }
    }

    private void sendFailedProviderServiceResponse(@NonNull UUID providerRequestId,
                                                   @NonNull ClientUserToken clientUserToken) {
        providerServiceResponseProducer.sendMessage(new FetchDataResultDTO(providerRequestId, ProviderServiceResponseStatus.UNKNOWN_ERROR), clientUserToken);
    }

    private void sendProviderServiceResponse(@NonNull UUID providerRequestId,
                                             @NonNull DataProviderResponse dataProviderResponse,
                                             @NonNull ClientUserToken clientUserToken,
                                             @NonNull Instant transactionsFetchStartTime,
                                             @NonNull UUID userSiteId,
                                             @NonNull UUID activityId,
                                             @NonNull String provider,
                                             @NonNull UUID siteId) {
        if (dataProviderResponse.isDataExpectedAsynchronousViaCallback()) {
            providerServiceResponseProducer.sendMessage(new FetchDataResultDTO(providerRequestId, ExtendedProviderServiceResponseStatus.FINISHED_WAITING_FOR_CALLBACK), clientUserToken);
            return;
        }

        if (dataProviderResponse.getAccounts().isEmpty()) {
            providerServiceResponseProducer.sendNoSupportedAccountsMessage(userSiteId, clientUserToken);
            accountsProducer.publishAccountAndTransactions(activityId, userSiteId, siteId, Collections.emptyList(), clientUserToken, provider);
        } else {
            DataProviderResponse filteredResponse = transactionsDataLimiter.limitResponseData(dataProviderResponse, transactionsFetchStartTime);
            List<IngestionAccountDTO> ingestionAccounts = filteredResponse.getAccounts().stream()
                    .map(it -> new IngestionAccountDTO(clientUserToken.getUserIdClaim(), userSiteId, siteId, provider, it))
                    .collect(Collectors.toList());
            accountsProducer.publishAccountAndTransactions(activityId, userSiteId, siteId, ingestionAccounts, clientUserToken, provider);
            providerServiceResponseProducer.sendMessage(new FetchDataResultDTO(providerRequestId, ProviderServiceResponseStatus.FINISHED), clientUserToken);
        }
    }

    @Async(ASYNC_PROVIDER_FETCH_DATA_EXECUTOR)
    public void submitMFA(String provider, FormSubmitMfaDTO submitMfaDTO, ClientUserToken clientUserToken) {
        UUID clientId = submitMfaDTO.getClientId();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        FormDataProvider dataProvider = providerFactoryService.getProvider(provider, FormDataProvider.class, AIS, STABLE);
        UUID providerRequestId = submitMfaDTO.getProviderRequestId();
        FormSubmitMfaRequest formSubmitMfaRequest = new FormSubmitMfaRequest(submitMfaDTO.getExternalSiteId(),
                submitMfaDTO.getFormUserSite(),
                submitMfaDTO.getAccessMeans(),
                submitMfaDTO.getTransactionsFetchStartTime(),
                submitMfaDTO.getMfaFormJson(),
                submitMfaDTO.getFilledInUserSiteFormValues(),
                loginSucceededDTO -> setStatusToLoginSucceeded(loginSucceededDTO, clientUserToken),
                this::setNewExternalId,
                authenticationDetails(clientId, clientUserToken, provider, dataProvider.getVersion()));
        try {
            DataProviderResponse unfilteredResponse = dataProvider.submitMfa(formSubmitMfaRequest);
            final UserSiteDataFetchInformation userSiteDataFetchInformation = submitMfaDTO.getUserSiteDataFetchInformation();
            DataProviderResponse filteredResponse = accountsFilterService.filterNormalResponse(provider, unfilteredResponse, userSiteDataFetchInformation, clientUserToken.getUserIdClaim());
            DataProviderResponse postProcessedResponse = accountsPostProcessingService.postProcessDataProviderResponse(filteredResponse);
            sendProviderServiceResponse(providerRequestId, postProcessedResponse, clientUserToken,
                    formSubmitMfaRequest.getTransactionsFetchStartTime(), userSiteDataFetchInformation.getUserSiteId(),
                    submitMfaDTO.getActivityId(), provider, submitMfaDTO.getSiteId());
        } catch (HandledProviderCheckedException e) {
            sendErrorResponse(e, providerRequestId, clientUserToken);
        } catch (ProviderFetchDataException e) {
            sendFailedProviderServiceResponse(providerRequestId, clientUserToken);
        } catch (RestClientException e) {
            handleRestClientException(e, providerRequestId, clientUserToken, "RestClientException while submitting mfa on a scraping provider.");
        } catch (RuntimeException e) {
            log.error("Unknown error while submitting mfa on a scraping provider.", e);
            sendFailedProviderServiceResponse(providerRequestId, clientUserToken);
        }
    }

    @Async(ASYNC_PROVIDER_FETCH_DATA_EXECUTOR)
    public void triggerRefreshAndFetchData(String provider, FormTriggerRefreshAndFetchDataDTO formTriggerRefreshAndFetchData, ClientUserToken clientUserToken) {
        UUID clientId = formTriggerRefreshAndFetchData.getClientId();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));


        FormDataProvider dataProvider = providerFactoryService.getProvider(provider, FormDataProvider.class, AIS, STABLE);
        UUID providerRequestId = formTriggerRefreshAndFetchData.getProviderRequestId();
        try {
            FormTriggerRefreshAndFetchDataRequest formTriggerRefreshAndFetchDataRequest = new FormTriggerRefreshAndFetchDataRequest(formTriggerRefreshAndFetchData.getExternalSiteId(),
                    formTriggerRefreshAndFetchData.getFormUserSite(),
                    formTriggerRefreshAndFetchData.getAccessMeans(),
                    formTriggerRefreshAndFetchData.getTransactionsFetchStartTime(),
                    loginSucceededDTO -> setStatusToLoginSucceeded(loginSucceededDTO, clientUserToken),
                    authenticationDetails(clientId, clientUserToken, provider, dataProvider.getVersion()));
            DataProviderResponse unfilteredResponse = dataProvider.triggerRefreshAndFetchData(formTriggerRefreshAndFetchDataRequest);
            final UserSiteDataFetchInformation userSiteDataFetchInformation = formTriggerRefreshAndFetchData.getUserSiteDataFetchInformation();
            DataProviderResponse filteredResponse = accountsFilterService.filterNormalResponse(provider, unfilteredResponse, userSiteDataFetchInformation, clientUserToken.getUserIdClaim());
            DataProviderResponse postProcessedResponse = accountsPostProcessingService.postProcessDataProviderResponse(filteredResponse);
            sendProviderServiceResponse(providerRequestId, postProcessedResponse, clientUserToken,
                    formTriggerRefreshAndFetchDataRequest.getTransactionsFetchStartTime(), userSiteDataFetchInformation.getUserSiteId(),
                    formTriggerRefreshAndFetchData.getActivityId(), provider, formTriggerRefreshAndFetchData.getSiteId());
        } catch (HandledProviderCheckedException e) {
            sendErrorResponse(e, providerRequestId, clientUserToken);
        } catch (ProviderFetchDataException e) {
            sendFailedProviderServiceResponse(providerRequestId, clientUserToken);
        } catch (RestClientException e) {
            handleRestClientException(e, providerRequestId, clientUserToken, "RestClientException while triggering a refresh and fetching data on a scraping provider.");
        } catch (RuntimeException e) {
            log.error("Unknown error while triggering a refresh and fetching data on a scraping provider", e);
            sendFailedProviderServiceResponse(providerRequestId, clientUserToken);
        }
    }

    public void processCallback(String provider, CallbackRequestDTO callbackRequestDTO, ClientUserToken clientUserToken) {
        CallbackDataProviderExtension dataProvider = providerFactoryService.getProvider(provider, CallbackDataProviderExtension.class, AIS, STABLE);
        if (!(dataProvider instanceof Provider)) {
            throw new IllegalStateException(String.format("Retrieved class for %s is not Provider instance.", provider));
        }
        UUID clientId = callbackRequestDTO.getClientId();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        UUID userId = clientUserToken.getUserIdClaim();
        try {
            CallbackResponseDTO unfilteredCallbackResponseDTO;
            if (callbackRequestDTO.getMoreInfo() == null) {
                FormCallbackProcessResponse formCallbackProcessResponse = new FormCallbackProcessResponse(callbackRequestDTO.getBody(), callbackRequestDTO.getSubPath(), authenticationDetails(callbackRequestDTO.getClientId(), clientUserToken, provider, ((Provider) dataProvider).getVersion()));
                unfilteredCallbackResponseDTO = dataProvider.process(formCallbackProcessResponse);
            } else {
                FormCallbackProcessWithMoreInfoResponse formCallbackProcessWithMoreInfoResponse = new FormCallbackProcessWithMoreInfoResponse(callbackRequestDTO.getBody(), callbackRequestDTO.getSubPath(), callbackRequestDTO.getMoreInfo(), authenticationDetails(callbackRequestDTO.getClientId(), clientUserToken, provider, ((Provider) dataProvider).getVersion()));

                unfilteredCallbackResponseDTO = dataProvider.process(formCallbackProcessWithMoreInfoResponse);
            }
            if (unfilteredCallbackResponseDTO instanceof UserDataCallbackResponse) {
                UserDataCallbackResponse filteredCallbackResponseDTO = accountsFilterService
                        .filterCallbackResponse(provider, (UserDataCallbackResponse) unfilteredCallbackResponseDTO, callbackRequestDTO.getUserSiteDataFetchInformation(), userId);
                UserDataCallbackResponse postProcessedCallbackResponseDTO = accountsPostProcessingService.postProcessUserDataCallbackResponse(filteredCallbackResponseDTO);
                UserSiteData userSiteData = postProcessedCallbackResponseDTO.getUserSiteData();
                String userSiteExternalId = userSiteData.getExternalUserSiteId();
                final UserSiteDataFetchInformation userSiteContext = callbackRequestDTO.getUserSiteDataFetchInformation().stream()
                        .filter(e -> Objects.equals(e.getUserSiteExternalId(), userSiteExternalId))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Never gonna happen"));
                UUID userSiteId = userSiteContext.getUserSiteId();
                UUID siteId = userSiteContext.getSiteId();

                UUID activeActivityId = callbackRequestDTO.getUserSiteToActiveActivityId().getOrDefault(userSiteId, CALLBACK_NO_ACTIVITY_ACTIVITY_ID);
                if (activeActivityId == null) {
                    activeActivityId = CALLBACK_NO_ACTIVITY_ACTIVITY_ID;
                }

                if (userSiteData.getAccounts().isEmpty()) {
                    sendFailedAccount(userSiteData.getFailedAccounts(), userId);
                    providerServiceResponseProducer.sendNoSupportedAccountsMessage(userSiteId, clientUserToken);
                    accountsProducer.publishAccountAndTransactions(activeActivityId, userSiteId, siteId, Collections.emptyList(), clientUserToken, provider);
                } else {
                    List<IngestionAccountDTO> ingestionAccounts = userSiteData.getAccounts().stream()
                            .map(it -> new IngestionAccountDTO(userId, userSiteId, siteId, provider, it))
                            .collect(Collectors.toList());
                    accountsProducer.publishAccountAndTransactions(activeActivityId, userSiteId, siteId, ingestionAccounts, clientUserToken, provider);
                    // Temporarily, we'll send the event like we were doing before *however*, without the accounts, so they will not be processed by site-management.
                    providerServiceResponseProducer.sendMessage(new UserDataCallbackResponse(provider, postProcessedCallbackResponseDTO.getExternalUserId(), new UserSiteData(
                            userSiteData.getExternalUserSiteId(), Collections.emptyList(), Collections.emptyList(), userSiteData.getProviderServiceResponseStatusValue()
                    )), clientUserToken);
                }
            } else {
                providerServiceResponseProducer.sendMessage(unfilteredCallbackResponseDTO, clientUserToken);
            }
        } catch (CallbackJsonParseException e) {
            log.error("going to publish json on kafka due to CallbackJsonParseException", e);
            errorTopicProducer.sendMessage(callbackRequestDTO.getBody());
        } catch (ExternalUserSiteDoesNotExistException e) {
            log.error("going to publish json on kafka due to ExternalUserSiteDoesNotExistException", e);
            errorTopicProducer.sendMessage(callbackRequestDTO.getBody());
        }
    }

    private void sendFailedAccount(List<DataProviderResponseFailedAccount> failedAccounts, UUID userId) {
        if (!failedAccounts.isEmpty()) {
            try {
                errorTopicProducer.sendMessage(objectMapper.writeValueAsString(failedAccounts), userId);
            } catch (JsonProcessingException e) {
                log.warn("Can't serialise failed accounts to json", new MessageSuppressingException(e));
            }
        }
    }

    @Async(ASYNC_PROVIDER_FETCH_EXTERNAL_USER_IDS_EXECUTOR)
    public void fetchExternalUserIdsFromProvider(String provider, UUID batchId, UUID clientId, ClientToken clientToken) {
        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        FormDataProvider dataProvider = providerFactoryService.getProvider(provider, FormDataProvider.class, AIS, STABLE);
        FormFetchExternalUserIdsFromProviderRequest formFetchExternalUserIdsFromProviderRequest = new FormFetchExternalUserIdsFromProviderRequest(clientId, externalUserIdsPublisher::sendMessageSync, batchId, externalUserIdsSliceLimit, authenticationDetails(clientId, clientToken, provider, dataProvider.getVersion()));
        dataProvider.fetchExternalUserIdsFromProvider(formFetchExternalUserIdsFromProviderRequest);
    }

    void deleteExternalUserByIdAtProvider(String provider, String externalUserId, UUID clientId, ClientToken clientToken) {
        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        FormDataProvider dataProvider = providerFactoryService.getProvider(provider, FormDataProvider.class, AIS, STABLE);
        FormDeleteExternalUserByIdRequest formDeleteExternalUserByIdRequest = new FormDeleteExternalUserByIdRequest(externalUserId, authenticationDetails(clientId, clientToken, provider, dataProvider.getVersion()));
        dataProvider.deleteExternalUserByIdAtProvider(formDeleteExternalUserByIdRequest);
    }

    private void sendErrorResponse(final HandledProviderCheckedException e, final UUID providerRequestId, ClientUserToken clientUserToken) {
        if (e instanceof MfaNeededException) {
            MfaNeededException mfaNeededException = (MfaNeededException) e;
            providerServiceResponseProducer.sendMessage(new ProviderServiceMAFResponseDTO(providerRequestId, mfaNeededException.getProviderMfaForm(), mfaNeededException.getYoltMfaForm(), mfaNeededException.getMfaTimeout()), clientUserToken);
            return;
        }

        ProviderServiceResponseStatusValue providerServiceResponseStatus = ExceptionToProviderStatusMapping.get(e.getClass());
        providerServiceResponseProducer.sendMessage(new FetchDataResultDTO(providerRequestId, providerServiceResponseStatus), clientUserToken);
    }

    private Void setStatusToLoginSucceeded(final LoginSucceededDTO loginSucceededDTO, ClientUserToken clientUserToken) {
        providerServiceResponseProducer.sendMessage(loginSucceededDTO, clientUserToken);
        return null;
    }

    private Void setNewExternalId(final SetExternalUserSiteIdDTO setExternalUserSiteIdDTO) {
        siteManagementClient.updateExternalUserSiteId(setExternalUserSiteIdDTO);
        return null;
    }

    private AuthenticationDetails authenticationDetails(UUID clientId, ClientToken clientToken, String provider, ProviderVersion providerVersion) {
        JcaSigner signer = jcaSignerFactory.getForClientToken(clientToken);
        RestTemplateManager restTemplateManager = restTemplateManagerCache.getForClientProvider(clientToken, AIS, provider, false, providerVersion);
        Map<String, BasicAuthenticationMean> authenticationMeans = clientAuthenticationMeansService.acquireAuthenticationMeansForScraping(provider, clientId);
        return new AuthenticationDetails(signer, restTemplateManager, authenticationMeans);
    }

    private void handleRestClientException(RestClientException exception,
                                           UUID providerRequestId,
                                           ClientUserToken clientUserToken,
                                           String message) {
        log.warn(message, exception);
        sendFailedProviderServiceResponse(providerRequestId, clientUserToken);
    }
}
