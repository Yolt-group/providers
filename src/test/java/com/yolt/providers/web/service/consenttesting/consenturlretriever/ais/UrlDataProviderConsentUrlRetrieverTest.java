package com.yolt.providers.web.service.consenttesting.consenturlretriever.ais;

import com.yolt.providers.common.domain.dynamic.step.FormStep;
import com.yolt.providers.common.domain.dynamic.step.RedirectStep;
import com.yolt.providers.common.providerinterface.FormDataProvider;
import com.yolt.providers.common.providerinterface.UrlDataProvider;
import com.yolt.providers.web.controller.dto.ApiGetLoginDTO;
import com.yolt.providers.web.service.ProviderService;
import com.yolt.providers.web.sitedetails.sites.SiteDetailsService;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.assertj.core.api.ThrowableAssert;
import org.jose4j.jwt.JwtClaims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlDataProviderConsentUrlRetrieverTest {

    private static final UUID SOME_CLIENT_ID = UUID.randomUUID();
    private static final UUID SOME_CLIENT_GROUP_ID = UUID.randomUUID();
    private static final UUID SOME_CLIENT_REDIRECT_ID = UUID.randomUUID();
    private static final UUID SOME_SITE_ID = UUID.randomUUID();
    private static final String SOME_URL = "http://somepage.com?redirectUri=https%3A%2F%2Fwww.yolt.com%2Fcallback-dev";
    private static final String SOME_PROVIDER = "SOME_PROVIDER";
    private static final ClientToken SOME_CLIENT_TOKEN = new ClientToken("some_serialized_token", new JwtClaims());

    @Mock
    private ProviderService providerService;
    @Mock
    private SiteDetailsService siteDetailsService;

    @InjectMocks
    private UrlDataProviderConsentUrlRetriever urlDataProviderLoginUrlRetriever;

    @Captor
    private ArgumentCaptor<ApiGetLoginDTO> apiGetLoginDTOArgumentCaptor;

    @Test
    void shouldRetrieveConsentUrlForGivenProviderForClient() {
        //given
        RedirectStep redirectUrl = new RedirectStep("redirectUrl");
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(SOME_CLIENT_ID, SOME_CLIENT_REDIRECT_ID);
        when(providerService.getLoginInfo(eq(SOME_PROVIDER), any(ApiGetLoginDTO.class), eq(SOME_CLIENT_TOKEN), any(UUID.class), eq(false)))
                .thenReturn(redirectUrl);
        when(siteDetailsService.getMatchingSiteIdForProviderKey(anyString())).thenReturn(Optional.of(SOME_SITE_ID.toString()));

        //when
        String result = urlDataProviderLoginUrlRetriever.retrieveConsentUrlForProvider(SOME_PROVIDER, authenticationMeansReference, SOME_URL, SOME_CLIENT_TOKEN);

        //then
        assertThat(result).isEqualTo("redirectUrl");
        verify(providerService).getLoginInfo(eq(SOME_PROVIDER), apiGetLoginDTOArgumentCaptor.capture(), eq(SOME_CLIENT_TOKEN), eq(SOME_SITE_ID), any(boolean.class));
        ApiGetLoginDTO capturedApiGetLoginDTO = apiGetLoginDTOArgumentCaptor.getValue();
        assertThat(capturedApiGetLoginDTO).extracting(ApiGetLoginDTO::getBaseClientRedirectUrl, ApiGetLoginDTO::getAuthenticationMeansReference, ApiGetLoginDTO::getExternalConsentId)
                .contains(SOME_URL, authenticationMeansReference, null);
    }

    @Test
    void shouldRetrieveConsentUrlForGivenProviderForClientGroup() {
        //given
        RedirectStep redirectUrl = new RedirectStep("redirectUrl");
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(null, SOME_CLIENT_GROUP_ID, SOME_CLIENT_REDIRECT_ID);
        when(providerService.getLoginInfo(eq(SOME_PROVIDER), any(ApiGetLoginDTO.class), eq(SOME_CLIENT_TOKEN), any(UUID.class), eq(false)))
                .thenReturn(redirectUrl);
        when(siteDetailsService.getMatchingSiteIdForProviderKey(anyString())).thenReturn(Optional.of(SOME_SITE_ID.toString()));

        //when
        String result = urlDataProviderLoginUrlRetriever.retrieveConsentUrlForProvider(SOME_PROVIDER, authenticationMeansReference, SOME_URL, SOME_CLIENT_TOKEN);

        //then
        assertThat(result).isEqualTo("redirectUrl");
        verify(providerService).getLoginInfo(eq(SOME_PROVIDER), apiGetLoginDTOArgumentCaptor.capture(), eq(SOME_CLIENT_TOKEN), eq(SOME_SITE_ID), eq(false));
        ApiGetLoginDTO capturedApiGetLoginDTO = apiGetLoginDTOArgumentCaptor.getValue();
        assertThat(capturedApiGetLoginDTO).extracting(ApiGetLoginDTO::getBaseClientRedirectUrl, ApiGetLoginDTO::getAuthenticationMeansReference, ApiGetLoginDTO::getExternalConsentId)
                .contains(SOME_URL, authenticationMeansReference, null);
    }

    @Test
    void shouldThrowIllegalStateExceptionIfGivenProviderWillReturnFormStep() {
        //given
        FormStep formStep = mock(FormStep.class);
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(SOME_CLIENT_ID, SOME_CLIENT_REDIRECT_ID);
        when(providerService.getLoginInfo(eq(SOME_PROVIDER), any(ApiGetLoginDTO.class), eq(SOME_CLIENT_TOKEN), any(UUID.class), eq(false)))
                .thenReturn(formStep);
        when(siteDetailsService.getMatchingSiteIdForProviderKey(anyString())).thenReturn(Optional.of(SOME_SITE_ID.toString()));


        //when
        ThrowableAssert.ThrowingCallable retrieveConsentUrlForProviderCallable = () ->
                urlDataProviderLoginUrlRetriever.retrieveConsentUrlForProvider(SOME_PROVIDER, authenticationMeansReference, SOME_URL, SOME_CLIENT_TOKEN);

        //then
        assertThatThrownBy(retrieveConsentUrlForProviderCallable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CONSENT TESTING - Only RedirectStep is supported");
    }

    @Test
    void shouldReturnTrueForSupportsWhenAppliedProviderOfTypeUrlDataProvider() {
        // given
        UrlDataProvider urlDataProvider = mock(UrlDataProvider.class);

        // when
        boolean result = urlDataProviderLoginUrlRetriever.supports(urlDataProvider);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseForSupportsWhenAppliedProviderOfTypeOtherThanUrlDataProvider() {
        // given
        FormDataProvider formDataProvider = mock(FormDataProvider.class);

        // when
        boolean result = urlDataProviderLoginUrlRetriever.supports(formDataProvider);

        // then
        assertThat(result).isFalse();
    }
}