package com.yolt.providers.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.ais.DataProviderResponse;
import com.yolt.providers.common.ais.form.FormUserSiteDataProviderResponse;
import com.yolt.providers.common.exception.ExternalUserSiteDoesNotExistException;
import com.yolt.providers.web.controller.dto.UserSiteDataFetchInformation;
import com.yolt.providers.web.service.domain.ProviderAccountUtil;
import nl.ing.lovebird.providerdomain.AccountType;
import nl.ing.lovebird.providerdomain.ProviderAccountDTO;
import nl.ing.lovebird.providerdomain.ProviderAccountNumberDTO;
import nl.ing.lovebird.providershared.callback.UserDataCallbackResponse;
import nl.ing.lovebird.providershared.callback.UserSiteData;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static nl.ing.lovebird.providershared.ProviderServiceResponseStatus.FINISHED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountsFilterServiceTest {

    private static final UUID USER_ID = UUID.fromString("06a1ccfd-9f31-41bb-a086-55050febfa57");
    private static final UUID SITE_ID = UUID.fromString("f8ba0a73-1aae-40ba-aa75-393cd1d20fee");
    private static final UUID USERSITE_ID = UUID.fromString("06e6c125-fd01-4086-a5b2-ca94956799a9");
    private static final String EXTERNAL_USER_ID = "external_user-123";
    private static final String EXTERNAL_USERSITE_ID = "external_usersite-123";
    private static final String DUMMY_JSON_STRING = "{}";
    private static final String PROVIDER_NAME = "TEST_PROVIDER";
    private static final String FILTER_ME_ACCOUNT_EXTERNAL_ID = "filter_me-123";
    private static final String ACCOUNT_EXTERNAL_ID = "external_account-123";
    private static final List<AccountType> CURRENT_ACCOUNT_LIST = Collections.singletonList(AccountType.CURRENT_ACCOUNT);
    private static final List<String> FILTER_ME_LIST = Collections.singletonList(FILTER_ME_ACCOUNT_EXTERNAL_ID);
    private static final UserSiteDataFetchInformation USERSITE_DATA_FETCH_INFORMATION = new UserSiteDataFetchInformation(EXTERNAL_USERSITE_ID, USERSITE_ID, SITE_ID, FILTER_ME_LIST, CURRENT_ACCOUNT_LIST);

    @Mock
    private ErrorTopicProducer errorTopicProducer;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ProviderAccountDTO providerAccountDTO;
    @Mock
    private ProviderAccountUtil providerAccountUtil;
    @InjectMocks
    private AccountsFilterService subject;

    @Test
    public void shouldReturnTheSameUserDataCallbackResponseForFilterCallbackResponseWithCorrectData() throws Exception {
        // given
        when(providerAccountUtil.validateAndFilterOptionalDetails(any(ProviderAccountDTO.class))).then(returnsFirstArg());
        when(providerAccountDTO.getYoltAccountType()).thenReturn(AccountType.CURRENT_ACCOUNT);
        UserSiteData userSiteData = new UserSiteData(EXTERNAL_USERSITE_ID, Collections.singletonList(providerAccountDTO), Collections.emptyList(), FINISHED);
        UserDataCallbackResponse callbackResponseDTO = new UserDataCallbackResponse(PROVIDER_NAME, EXTERNAL_USER_ID, userSiteData);

        // when
        final UserDataCallbackResponse actual = subject.filterCallbackResponse(PROVIDER_NAME, callbackResponseDTO, Collections.singletonList(USERSITE_DATA_FETCH_INFORMATION), USER_ID);

        // then
        final UserSiteData actualUserSiteData = actual.getUserSiteData();
        assertThat(actualUserSiteData).isEqualTo(userSiteData);
    }

    @Test
    public void shouldReturnEmptyListForFilterCallbackResponseAndFilterNormalResponseWithAccountTypeThatIsNotWhitelisted() throws Exception {
        // given
        when(providerAccountDTO.getYoltAccountType()).thenReturn(AccountType.SAVINGS_ACCOUNT);
        UserSiteData userSiteData = new UserSiteData(EXTERNAL_USERSITE_ID, Collections.singletonList(providerAccountDTO), Collections.emptyList(), FINISHED);
        UserDataCallbackResponse callbackResponseDTO = new UserDataCallbackResponse(PROVIDER_NAME, EXTERNAL_USER_ID, userSiteData);

        // when
        List<ProviderAccountDTO> filteredAccountsCallbackResponse =
                subject.filterCallbackResponse(PROVIDER_NAME, callbackResponseDTO, Collections.singletonList(USERSITE_DATA_FETCH_INFORMATION), USER_ID)
                        .getUserSiteData().getAccounts();
        List<ProviderAccountDTO> filteredAccountsNormalResponse =
                subject.filterNormalResponse(PROVIDER_NAME, new DataProviderResponse(Collections.singletonList(providerAccountDTO)), USERSITE_DATA_FETCH_INFORMATION, USER_ID)
                        .getAccounts();

        // then
        assertThat(filteredAccountsCallbackResponse).isEmpty();
        assertThat(filteredAccountsNormalResponse).isEmpty();
    }

    @Test
    public void shouldReturnEmptyListForFilterCallbackResponseAndFilterNormalResponseWithAccountIdThatIsNotListedInUserSiteMigratedAccountIds() throws Exception {
        // given
        when(providerAccountDTO.getYoltAccountType()).thenReturn(AccountType.CURRENT_ACCOUNT);
        when(providerAccountDTO.getAccountId()).thenReturn(FILTER_ME_ACCOUNT_EXTERNAL_ID);
        UserSiteData userSiteData = new UserSiteData(EXTERNAL_USERSITE_ID, Collections.singletonList(providerAccountDTO), Collections.emptyList(), FINISHED);
        UserDataCallbackResponse callbackResponseDTO = new UserDataCallbackResponse(PROVIDER_NAME, EXTERNAL_USER_ID, userSiteData);

        // when
        List<ProviderAccountDTO> filteredAccountsCallbackResponse =
                subject.filterCallbackResponse(PROVIDER_NAME, callbackResponseDTO, Collections.singletonList(USERSITE_DATA_FETCH_INFORMATION), USER_ID)
                        .getUserSiteData().getAccounts();

        List<ProviderAccountDTO> filteredAccountsNormalResponse =
                subject.filterNormalResponse(PROVIDER_NAME, new DataProviderResponse(Collections.singletonList(providerAccountDTO)), USERSITE_DATA_FETCH_INFORMATION, USER_ID)
                        .getAccounts();

        // then
        assertThat(filteredAccountsCallbackResponse).isEmpty();
        assertThat(filteredAccountsNormalResponse).isEmpty();
    }

    @Test
    public void shouldThrowExternalUserSiteDoesNotExistExceptionForFilterCallbackResponseWhenUserSiteIsMissing() {
        // given
        UserSiteData userSiteData = new UserSiteData(EXTERNAL_USERSITE_ID, Collections.emptyList(), Collections.emptyList(), FINISHED);
        UserDataCallbackResponse callbackResponseDTO = new UserDataCallbackResponse(PROVIDER_NAME, EXTERNAL_USER_ID, userSiteData);

        // when
        ThrowableAssert.ThrowingCallable filterCallbackResponseCallable = () -> subject.filterCallbackResponse(PROVIDER_NAME, callbackResponseDTO, Collections.emptyList(), USER_ID);

        // then
        assertThatThrownBy(filterCallbackResponseCallable)
                .isInstanceOf(ExternalUserSiteDoesNotExistException.class);
    }

    @Test
    public void shouldReturnTheSameDataProviderResponseForFilterNormalResponseWithCorrectData() {
        // given
        when(providerAccountUtil.validateAndFilterOptionalDetails(any(ProviderAccountDTO.class))).then(returnsFirstArg());
        when(providerAccountDTO.getYoltAccountType()).thenReturn(AccountType.CURRENT_ACCOUNT);
        when(providerAccountDTO.getAccountId()).thenReturn(ACCOUNT_EXTERNAL_ID);
        final DataProviderResponse dataProviderResponse = new DataProviderResponse(Collections.singletonList(providerAccountDTO));

        // when
        DataProviderResponse actualDataProviderResponse =
                subject.filterNormalResponse(PROVIDER_NAME, dataProviderResponse, USERSITE_DATA_FETCH_INFORMATION, USER_ID);

        // then
        assertThat(actualDataProviderResponse).isEqualTo(dataProviderResponse);
    }

    @Test
    public void shouldReturnTheSameFormUserSiteDataProviderResponseForFilterNormalResponseWithCorrectData() {
        // given
        final DataProviderResponse dataProviderResponse = new FormUserSiteDataProviderResponse(Collections.singletonList(providerAccountDTO), FormUserSiteDataProviderResponse.Status.FINISHED);

        // when
        DataProviderResponse actualDataProviderResponse =
                subject.filterNormalResponse(PROVIDER_NAME, dataProviderResponse, USERSITE_DATA_FETCH_INFORMATION, USER_ID);

        // then
        assertThat(actualDataProviderResponse).isEqualTo(dataProviderResponse);
    }

    @Test
    public void shouldSendMessageToKafkaTopicForFilterCallbackResponseAndFilterNormalResponseWhenFilteringInvalidAccount() throws Exception {
        // given
        when(providerAccountUtil.validateAndFilterOptionalDetails(any(ProviderAccountDTO.class))).thenCallRealMethod();
        when(objectMapper.writeValueAsString(any())).thenReturn(DUMMY_JSON_STRING);
        UserSiteData userSiteData = new UserSiteData(EXTERNAL_USERSITE_ID, Collections.singletonList(providerAccountDTO), Collections.emptyList(), FINISHED);
        UserDataCallbackResponse callbackResponseDTO = new UserDataCallbackResponse(PROVIDER_NAME, EXTERNAL_USER_ID, userSiteData);

        // when
        List<ProviderAccountDTO> filteredAccountsCallbackResponse =
                subject.filterCallbackResponse(PROVIDER_NAME, callbackResponseDTO, Collections.singletonList(USERSITE_DATA_FETCH_INFORMATION), USER_ID)
                        .getUserSiteData().getAccounts();
        List<ProviderAccountDTO> filteredAccountsNormalResponse =
                subject.filterNormalResponse(PROVIDER_NAME, new DataProviderResponse(Collections.singletonList(providerAccountDTO)), USERSITE_DATA_FETCH_INFORMATION, USER_ID)
                        .getAccounts();

        assertThat(filteredAccountsCallbackResponse).isEmpty();
        assertThat(filteredAccountsNormalResponse).isEmpty();
        verify(errorTopicProducer, times(2)).sendMessage(DUMMY_JSON_STRING, USER_ID);
    }

}
