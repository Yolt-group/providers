package com.yolt.providers.web.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.ais.DataProviderResponse;
import com.yolt.providers.common.ais.form.*;
import com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans;
import com.yolt.providers.common.domain.consenttesting.ConsentValidityRules;
import com.yolt.providers.common.exception.*;
import com.yolt.providers.common.providerinterface.FormDataProvider;
import com.yolt.providers.common.providerinterface.extension.CallbackDataProviderExtension;
import com.yolt.providers.common.versioning.ProviderVersion;
import com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService;
import com.yolt.providers.web.controller.dto.FormCreateNewExternalUserSiteDTO;
import com.yolt.providers.web.controller.dto.FormTriggerRefreshAndFetchDataDTO;
import com.yolt.providers.web.controller.dto.FormUpdateExternalUserSiteDTO;
import com.yolt.providers.web.controller.dto.*;
import com.yolt.providers.web.cryptography.signing.JcaSignerFactory;
import com.yolt.providers.web.cryptography.transport.MutualTLSRestTemplateManagerCache;
import com.yolt.providers.web.form.externalids.ProviderExternalUserIdsSyncKafkaPublisher;
import com.yolt.providers.web.service.*;
import com.yolt.providers.web.service.configuration.VersionType;
import com.yolt.providers.web.service.dto.FetchDataResultDTO;
import lombok.AllArgsConstructor;
import nl.ing.lovebird.clienttokens.ClientUserToken;
import nl.ing.lovebird.extendeddata.common.CurrencyCode;
import nl.ing.lovebird.providerdomain.AccountType;
import nl.ing.lovebird.providerdomain.ProviderAccountDTO;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providershared.AccessMeansDTO;
import nl.ing.lovebird.providershared.ProviderServiceResponseStatus;
import nl.ing.lovebird.providershared.callback.CallbackResponseDTO;
import nl.ing.lovebird.providershared.callback.UserDataCallbackResponse;
import nl.ing.lovebird.providershared.callback.UserSiteData;
import nl.ing.lovebird.providershared.form.*;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;

import static com.yolt.providers.common.versioning.ProviderVersion.VERSION_1;
import static nl.ing.lovebird.providerdomain.ServiceType.AIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FormProviderServiceTest {

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final ClientUserToken CLIENT_USER_TOKEN = mock(ClientUserToken.class);
    private static final String BUDGET_INSIGHT_NAME = "BUDGET_INSIGHT";

    @Mock
    private ProviderFactoryService providerFactoryService;
    @Mock
    private FormDataProvider formDataProvider;
    @Mock
    private ProviderServiceResponseProducer providerServiceResponseProducer;
    @Mock
    private ErrorTopicProducer errorTopicProducer;
    @Mock
    private ProviderExternalUserIdsSyncKafkaPublisher externalUserIdsPublisher;
    @Mock
    private SiteManagementClient siteManagementClient;
    @Mock
    private ClientAuthenticationMeansService clientAuthenticationMeansService;
    @Mock
    private JcaSignerFactory jcaSignerFactory;
    @Mock
    private MutualTLSRestTemplateManagerCache mutualTLSRestTemplateManagerCache;
    @Mock
    private AccountsFilterService accountsFilterService;
    @Mock
    private AccountsPostProcessingService accountsPostProcessingService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private AccountsProducer accountsProducer;

    private FormProviderService formProviderService;

    @BeforeEach
    public void beforeEach() {
        TransactionsDataLimiter transactionsDataLimiter = new TransactionsDataLimiter(true);
        formProviderService = new FormProviderService(providerFactoryService, transactionsDataLimiter, providerServiceResponseProducer, errorTopicProducer, externalUserIdsPublisher, 0, siteManagementClient, clientAuthenticationMeansService,
                jcaSignerFactory, mutualTLSRestTemplateManagerCache, accountsFilterService, accountsPostProcessingService, objectMapper, accountsProducer);
    }

    @Test
    public void shouldReturnRefreshedAccessMeansForRefreshAccessMeansForFormWithCorrectData() throws ExternalUserSiteDoesNotExistException {
        // given
        FormRefreshAccessMeansDTO accessMeansDTO = new FormRefreshAccessMeansDTO(new AccessMeansDTO(UUID.randomUUID(), "my-access-means", new Date(), new Date()), CLIENT_ID);
        AccessMeansDTO refreshedAccessMeansDTO = new AccessMeansDTO(UUID.randomUUID(), "my-refreshed-access-means", new Date(), new Date());
        when(providerFactoryService.getProvider(eq(BUDGET_INSIGHT_NAME), eq(FormDataProvider.class), eq(AIS), any(VersionType.class))).thenReturn(formDataProvider);
        when(formDataProvider.refreshAccessMeans(any())).thenReturn(refreshedAccessMeansDTO);

        // when
        AccessMeansDTO refreshedAccessMeans = formProviderService.refreshAccessMeansForForm(BUDGET_INSIGHT_NAME, CLIENT_USER_TOKEN, accessMeansDTO);

        // then
        assertThat(refreshedAccessMeans.getAccessMeans()).isEqualTo(refreshedAccessMeansDTO.getAccessMeans());
        ArgumentCaptor<FormRefreshAccessMeansRequest> accessMeansDTOArgumentCaptor = ArgumentCaptor.forClass(FormRefreshAccessMeansRequest.class);
        verify(formDataProvider).refreshAccessMeans(accessMeansDTOArgumentCaptor.capture());
        assertThat(accessMeansDTOArgumentCaptor.getValue().getAccessMeans().getAccessMeans()).isEqualTo(accessMeansDTO.getAccessMeansDTO().getAccessMeans());
    }

    @Test
    public void shouldCallUpdateExternalUserSiteOnFormDataProviderForUpdateExternalUserSiteWithCorrectData() throws HandledProviderCheckedException, ProviderFetchDataException {
        // given
        FormUpdateExternalUserSiteDTO formUpdateExternalUserSiteDTO = new FormUpdateExternalUserSiteDTO(null, null, "my-access-means", Instant.now(), null, null, null, UUID.randomUUID(), new UserSiteDataFetchInformation(null, null, null, Collections.emptyList(), Collections.emptyList()), UUID.randomUUID(), UUID.randomUUID());
        when(providerFactoryService.getProvider(eq(BUDGET_INSIGHT_NAME), eq(FormDataProvider.class), eq(AIS), any(VersionType.class))).thenReturn(formDataProvider);
        DataProviderResponse dataProviderResponse = new DataProviderResponse(Collections.emptyList());
        when(formDataProvider.updateExternalUserSite(any())).thenReturn(dataProviderResponse);

        // when
        formProviderService.updateExternalUserSite(BUDGET_INSIGHT_NAME, formUpdateExternalUserSiteDTO, CLIENT_USER_TOKEN);

        // then
        ArgumentCaptor<FormUpdateExternalUserSiteRequest> formUpdateExternalUserSiteDTOArgumentCaptor = ArgumentCaptor.forClass(FormUpdateExternalUserSiteRequest.class);
        verify(formDataProvider).updateExternalUserSite(formUpdateExternalUserSiteDTOArgumentCaptor.capture());
        assertThat(formUpdateExternalUserSiteDTOArgumentCaptor.getValue().getAccessMeans()).isEqualTo(formUpdateExternalUserSiteDTO.getAccessMeans());
        verify(accountsFilterService).filterNormalResponse(any(), any(), any(), any());
    }

    @Test
    public void shouldCallCreateNewExternalUserSiteOnFormDataProviderForCreateNewExternalUserSiteWithCorrectData() throws ProviderFetchDataException, HandledProviderCheckedException {
        // given
        AccessMeansDTO accessMeansDTO = new AccessMeansDTO(UUID.randomUUID(), "my-access-means", new Date(), new Date());
        FormCreateNewExternalUserSiteDTO formFetchDataDTO = new FormCreateNewExternalUserSiteDTO(null, null, null, accessMeansDTO, Instant.now(), null, null, null, UUID.randomUUID(), new UserSiteDataFetchInformation(null, null, null, Collections.emptyList(), Collections.emptyList()), UUID.randomUUID(), UUID.randomUUID());
        when(providerFactoryService.getProvider(eq(BUDGET_INSIGHT_NAME), eq(FormDataProvider.class), eq(AIS), any(VersionType.class))).thenReturn(formDataProvider);
        DataProviderResponse dataProviderResponse = new DataProviderResponse(Collections.emptyList());
        when(formDataProvider.createNewExternalUserSite(any())).thenReturn(dataProviderResponse);

        // when
        formProviderService.createNewExternalUserSite(BUDGET_INSIGHT_NAME, formFetchDataDTO, CLIENT_USER_TOKEN);

        // when
        ArgumentCaptor<FormCreateNewExternalUserSiteRequest> captor = ArgumentCaptor.forClass(FormCreateNewExternalUserSiteRequest.class);
        verify(formDataProvider).createNewExternalUserSite(captor.capture());
        assertThat(captor.getValue().getAccessMeans().getAccessMeans()).isEqualTo(accessMeansDTO.getAccessMeans());
        verify(accountsFilterService).filterNormalResponse(any(), any(), any(), any());
    }

    @Test
    public void shouldReturnNewUserResponseForCreateNewUserWithCorrectData() {
        // given
        FormCreateNewUserRequestDTO formCreateNewUserRequestDTO = new FormCreateNewUserRequestDTO(null, null, null, false);
        when(providerFactoryService.getProvider(eq(BUDGET_INSIGHT_NAME), eq(FormDataProvider.class), eq(AIS), any(VersionType.class))).thenReturn(formDataProvider);
        when(formDataProvider.createNewUser(any())).thenReturn(new FormCreateNewUserResponse(
                new AccessMeansDTO(UUID.randomUUID(), "my-access-means", new Date(), new Date()),
                "extUserId"));

        // when
        FormCreateNewUserResponse newUser = formProviderService.createNewUser(BUDGET_INSIGHT_NAME, CLIENT_USER_TOKEN, formCreateNewUserRequestDTO);

        // then
        assertThat(newUser.getAccessMeans().getAccessMeans()).isEqualTo("my-access-means");
    }

    @Test
    public void shouldCallSubmitMfaOnFormDataProviderForSubmitMfaWithCorrectData() throws ProviderFetchDataException, HandledProviderCheckedException {
        // given
        FormSubmitMfaDTO formSubmitMfaDTO = new FormSubmitMfaDTO(null, null, "my-access-means", Instant.now(), null, null, null, UUID.randomUUID(), new UserSiteDataFetchInformation(null, null, null, Collections.emptyList(), Collections.emptyList()), UUID.randomUUID(), UUID.randomUUID());
        when(providerFactoryService.getProvider(eq(BUDGET_INSIGHT_NAME), eq(FormDataProvider.class), eq(AIS), any(VersionType.class))).thenReturn(formDataProvider);
        DataProviderResponse dataProviderResponse = new DataProviderResponse(Collections.emptyList());
        when(formDataProvider.submitMfa(any())).thenReturn(dataProviderResponse);

        // when
        formProviderService.submitMFA(BUDGET_INSIGHT_NAME, formSubmitMfaDTO, CLIENT_USER_TOKEN);

        // then
        ArgumentCaptor<FormSubmitMfaRequest> captor = ArgumentCaptor.forClass(FormSubmitMfaRequest.class);
        verify(formDataProvider).submitMfa(captor.capture());
        assertThat(captor.getValue().getAccessMeans()).isEqualTo(formSubmitMfaDTO.getAccessMeans());
        verify(accountsFilterService).filterNormalResponse(any(), any(), any(), any());
    }

    @Test
    public void shouldCallDeleteUserSiteOnFormDataProviderForDeleteUserSiteWithCorrectData() throws AccessMeansExpiredException {
        // given
        FormDeleteUserSiteDTO formDeleteUserSite = new FormDeleteUserSiteDTO("my-access-means", null, null, null);
        when(providerFactoryService.getProvider(eq(BUDGET_INSIGHT_NAME), eq(FormDataProvider.class), eq(AIS), any(VersionType.class))).thenReturn(formDataProvider);

        // when
        formProviderService.deleteUserSite(BUDGET_INSIGHT_NAME, CLIENT_USER_TOKEN, formDeleteUserSite);

        // then
        ArgumentCaptor<FormDeleteUserSiteRequest> captor = ArgumentCaptor.forClass(FormDeleteUserSiteRequest.class);
        verify(formDataProvider).deleteUserSite(captor.capture());
        assertThat(captor.getValue().getAccessMeans()).isEqualTo(formDeleteUserSite.getAccessMeans());
    }

    @Test
    public void shouldCallDeleteUserOnFormDataProviderForDeleteUserWithCorrectData() throws AccessMeansExpiredException {
        // given
        FormDeleteUser formDeleteUser = new FormDeleteUser(new AccessMeansDTO(UUID.randomUUID(), "my-access-means", new Date(), new Date()), null);
        when(providerFactoryService.getProvider(eq(BUDGET_INSIGHT_NAME), eq(FormDataProvider.class), eq(AIS), any(VersionType.class))).thenReturn(formDataProvider);

        // when
        formProviderService.deleteUser(BUDGET_INSIGHT_NAME, CLIENT_USER_TOKEN, formDeleteUser);

        // then
        ArgumentCaptor<FormDeleteUserRequest> captor = ArgumentCaptor.forClass(FormDeleteUserRequest.class);
        verify(formDataProvider).deleteUser(captor.capture());
        assertThat(captor.getValue().getAccessMeans().getAccessMeans()).isEqualTo(formDeleteUser.getAccessMeansDTO().getAccessMeans());
    }

    @Test
    public void shouldCallTriggerRefreshAndFetchDataOnFormDataProviderForTriggerRefreshAndFetchDataWithCorrectData() throws ProviderFetchDataException, HandledProviderCheckedException {
        // given
        UUID activityId = UUID.randomUUID();
        UUID userSiteId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        AccessMeansDTO accessMeansDTO = new AccessMeansDTO(UUID.randomUUID(), "my-access-means", new Date(), new Date());
        FormTriggerRefreshAndFetchDataDTO triggerRefreshAndFetchDataDTO = new FormTriggerRefreshAndFetchDataDTO(null, null, accessMeansDTO, Instant.now(), UUID.randomUUID(), null, new UserSiteDataFetchInformation(null, userSiteId, siteId, Collections.emptyList(), Collections.emptyList()), activityId, siteId);
        when(providerFactoryService.getProvider(eq(BUDGET_INSIGHT_NAME), eq(FormDataProvider.class), eq(AIS), any(VersionType.class))).thenReturn(formDataProvider);
        DataProviderResponse dataProviderResponse = new DataProviderResponse(Collections.singletonList(new ProviderAccountDTO(AccountType.CURRENT_ACCOUNT, ZonedDateTime.now(),
                BigDecimal.ONE, null, "1234", "masked", null, null, "accountname", CurrencyCode.EUR,
                false, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null, null, null)));
        when(formDataProvider.triggerRefreshAndFetchData(any())).thenReturn(dataProviderResponse);
        when(accountsFilterService.filterNormalResponse(anyString(), any(), any(), any())).thenAnswer(AdditionalAnswers.returnsSecondArg());
        when(accountsPostProcessingService.postProcessDataProviderResponse(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());

        // when
        formProviderService.triggerRefreshAndFetchData(BUDGET_INSIGHT_NAME, triggerRefreshAndFetchDataDTO, CLIENT_USER_TOKEN);

        // then
        ArgumentCaptor<FormTriggerRefreshAndFetchDataRequest> formTriggerRefreshAndFetchDataDTOArgumentCaptor = ArgumentCaptor.forClass(FormTriggerRefreshAndFetchDataRequest.class);
        verify(formDataProvider).triggerRefreshAndFetchData(formTriggerRefreshAndFetchDataDTOArgumentCaptor.capture());
        assertThat(formTriggerRefreshAndFetchDataDTOArgumentCaptor.getValue().getAccessMeans().getAccessMeans()).isEqualTo(accessMeansDTO.getAccessMeans());
        verify(accountsFilterService).filterNormalResponse(any(), any(), any(), any());
        verify(accountsProducer).publishAccountAndTransactions(eq(activityId), eq(userSiteId), eq(siteId), anyList(), eq(CLIENT_USER_TOKEN), anyString());
    }

    @Test
    public void shouldPublishAccountAndTransactionsForProcessCallbackWhenDataComeInForAUserSiteWithAnActiveActivity() throws ExternalUserSiteDoesNotExistException {
        // given
        String externalUserSiteId = "externalUserSiteId";
        TempProvider tempProvider = new TempProvider(externalUserSiteId);
        UUID activityIdForUserSite = UUID.randomUUID();
        UUID userSite = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        List<UserSiteDataFetchInformation> userSiteDataFetchInformations = Collections.singletonList(
                new UserSiteDataFetchInformation(externalUserSiteId, userSite, siteId, null, null)
        );
        Map<UUID, UUID> userSiteToActiveActivityId = new HashMap<>() {{
            put(userSite, activityIdForUserSite);
        }};
        when(providerFactoryService.getProvider(eq("BUDGET_INSIGHT"), eq(CallbackDataProviderExtension.class), eq(AIS), any(VersionType.class))).thenReturn(tempProvider);
        when(accountsFilterService.filterCallbackResponse(anyString(), any(), any(), any())).thenAnswer(AdditionalAnswers.returnsSecondArg());
        when(accountsPostProcessingService.postProcessUserDataCallbackResponse(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());


        // when
        formProviderService.processCallback(
                "BUDGET_INSIGHT",
                new CallbackRequestDTO("somebody", null, null, UUID.randomUUID(), userSiteDataFetchInformations, userSiteToActiveActivityId),
                CLIENT_USER_TOKEN);

        // then
        verify(accountsProducer).publishAccountAndTransactions(eq(activityIdForUserSite), eq(userSite), eq(siteId), anyList(), eq(CLIENT_USER_TOKEN), anyString());
    }

    @Test
    public void shouldPublishAccountAndTransactionsWithSpecialActivityIdForProcessCallbackWhenDataComeInForAUserSiteWithoutActiveActivity() throws ExternalUserSiteDoesNotExistException {
        // given
        String externalUserSiteId = "externalUserSiteId";
        TempProvider testProvider = new TempProvider(externalUserSiteId);
        UUID userSite = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        List<UserSiteDataFetchInformation> userSiteDataFetchInformations = Collections.singletonList(
                new UserSiteDataFetchInformation(externalUserSiteId, userSite, siteId, null, null)
        );
        Map<UUID, UUID> userSiteToActiveActivityId = Collections.emptyMap();
        when(providerFactoryService.getProvider(eq("BUDGET_INSIGHT"), eq(CallbackDataProviderExtension.class), eq(AIS), any(VersionType.class))).thenReturn(testProvider);
        when(accountsFilterService.filterCallbackResponse(anyString(), any(), any(), any())).thenAnswer(AdditionalAnswers.returnsSecondArg());
        when(accountsPostProcessingService.postProcessUserDataCallbackResponse(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());

        // when
        formProviderService.processCallback(
                "BUDGET_INSIGHT",
                new CallbackRequestDTO("somebody", null, null, UUID.randomUUID(), userSiteDataFetchInformations, userSiteToActiveActivityId),
                CLIENT_USER_TOKEN);

        // then
        UUID specialActivityId = new UUID(0, 0);
        verify(accountsProducer).publishAccountAndTransactions(eq(specialActivityId), eq(userSite), eq(siteId), anyList(), eq(CLIENT_USER_TOKEN), anyString());
    }

    @Test
    public void shouldSendIncorrectCredentialsMessageForTriggerRefreshAndFetchDataWhenIncorrectCredentialsException() throws ProviderFetchDataException, HandledProviderCheckedException {
        // given
        UUID userId = UUID.randomUUID();
        UUID providerRequestId = UUID.randomUUID();
        when(providerFactoryService.getProvider(eq(BUDGET_INSIGHT_NAME), eq(FormDataProvider.class), eq(AIS), any(VersionType.class))).thenReturn(formDataProvider);
        when(formDataProvider.triggerRefreshAndFetchData(any())).thenThrow(new IncorrectCredentialsException());
        FormTriggerRefreshAndFetchDataDTO triggerRefreshAndFetchDataDTO = new FormTriggerRefreshAndFetchDataDTO("extSiteId", new FormUserSiteDTO(userId, UUID.randomUUID(), "extUserSiteId"), new AccessMeansDTO(userId, "my-access-mean", new Date(), new Date()), Instant.now(), providerRequestId, UUID.randomUUID(), new UserSiteDataFetchInformation(null, null, null, Collections.emptyList(), Collections.emptyList()), UUID.randomUUID(), UUID.randomUUID());

        // when
        formProviderService.triggerRefreshAndFetchData(BUDGET_INSIGHT_NAME, triggerRefreshAndFetchDataDTO, CLIENT_USER_TOKEN);

        // then
        verify(providerServiceResponseProducer).sendMessage(new FetchDataResultDTO(providerRequestId, ExtendedProviderServiceResponseStatus.INCORRECT_CREDENTIALS), CLIENT_USER_TOKEN);
    }

    @AllArgsConstructor
    private class TempProvider implements CallbackDataProviderExtension, FormDataProvider {

        private String externalUserSiteId;

        @Override
        public Map<String, TypedAuthenticationMeans> getTypedAuthenticationMeans() {
            throw new NotImplementedException();
        }

        @Override
        public ConsentValidityRules getConsentValidityRules() {
            return ConsentValidityRules.EMPTY_RULES_SET;
        }

        @Override
        public String getProviderIdentifier() {
            throw new NotImplementedException();
        }

        @Override
        public String getProviderIdentifierDisplayName() {
            throw new NotImplementedException();
        }

        @Override
        public ServiceType getServiceType() {
            throw new NotImplementedException();
        }

        @Override
        public DataProviderResponse fetchData(final FormFetchDataRequest formFetchDataRequest) throws ProviderFetchDataException, HandledProviderCheckedException {
            throw new NotImplementedException();
        }

        @Override
        public DataProviderResponse updateExternalUserSite(final FormUpdateExternalUserSiteRequest formUpdateExternalUserSite) throws ProviderFetchDataException, HandledProviderCheckedException {
            throw new NotImplementedException();
        }

        @Override
        public DataProviderResponse createNewExternalUserSite(final FormCreateNewExternalUserSiteRequest formCreateNewExternalUserSite) throws ProviderFetchDataException, HandledProviderCheckedException {
            throw new NotImplementedException();
        }

        @Override
        public DataProviderResponse submitMfa(final FormSubmitMfaRequest formSubmitMfaRequest) throws ProviderFetchDataException, HandledProviderCheckedException {
            throw new NotImplementedException();
        }

        @Override
        public void deleteUserSite(final FormDeleteUserSiteRequest formDeleteUserSite) throws AccessMeansExpiredException {
            throw new NotImplementedException();

        }

        @Override
        public void deleteUser(final FormDeleteUserRequest formDeleteUserRequest) throws AccessMeansExpiredException {
            throw new NotImplementedException();

        }

        @Override
        public LoginFormResponse fetchLoginForm(final FormFetchLoginFormRequest formFetchLoginFormRequest) throws IOException {
            throw new NotImplementedException();
        }

        @Override
        public DataProviderResponse triggerRefreshAndFetchData(final FormTriggerRefreshAndFetchDataRequest formTriggerRefreshAndFetchDataRequest) throws ProviderFetchDataException, HandledProviderCheckedException {
            throw new NotImplementedException();
        }

        @Override
        public EncryptionDetails getEncryptionDetails(final AuthenticationDetails authenticationDetails) {
            throw new NotImplementedException();
        }

        @Override
        public FormCreateNewUserResponse createNewUser(final FormCreateNewUserRequest formCreateNewUserRequest) {
            throw new NotImplementedException();
        }

        @Override
        public AccessMeansDTO refreshAccessMeans(final FormRefreshAccessMeansRequest refreshAccessMeans) throws ExternalUserSiteDoesNotExistException {
            throw new NotImplementedException();
        }

        @Override
        public void fetchExternalUserIdsFromProvider(final FormFetchExternalUserIdsFromProviderRequest formFetchExternalUserIdsFromProviderRequest) {
            throw new NotImplementedException();
        }

        @Override
        public void deleteExternalUserByIdAtProvider(final FormDeleteExternalUserByIdRequest formDeleteExternalUserByIdRequest) {
            throw new NotImplementedException();
        }

        @Override
        public ProviderVersion getVersion() {
            return VERSION_1;
        }

        @Override
        public CallbackResponseDTO process(final FormCallbackProcessResponse formCallbackProcessResponse) throws CallbackJsonParseException {
            return new UserDataCallbackResponse("SALTEDGE", "extUserId",
                    new UserSiteData(externalUserSiteId, Collections.singletonList(
                            new ProviderAccountDTO(AccountType.CURRENT_ACCOUNT, ZonedDateTime.now(),
                                    BigDecimal.ONE, null, "1234", "masked", null, null, "accountname", CurrencyCode.EUR,
                                    false, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null, null, null)),
                            Collections.emptyList(), ProviderServiceResponseStatus.FINISHED)
            );
        }

        @Override
        public CallbackResponseDTO process(final FormCallbackProcessWithMoreInfoResponse formCallbackProcessWithMoreInfoResponse) throws CallbackJsonParseException {
            throw new NotImplementedException();
        }
    }
}