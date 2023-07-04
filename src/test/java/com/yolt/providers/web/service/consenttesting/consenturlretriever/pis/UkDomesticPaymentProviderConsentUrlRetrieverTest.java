package com.yolt.providers.web.service.consenttesting.consenturlretriever.pis;

import com.yolt.providers.common.pis.ukdomestic.AccountIdentifierScheme;
import com.yolt.providers.common.pis.ukdomestic.UkAccountDTO;
import com.yolt.providers.common.providerinterface.UkDomesticPaymentProvider;
import com.yolt.providers.web.controller.dto.ExternalInitiateUkDomesticPaymentResponseDTO;
import com.yolt.providers.web.controller.dto.ExternalInitiateUkDomesticScheduledPaymentRequestDTO;
import com.yolt.providers.web.controller.dto.ExternalInitiateUkScheduledPaymentRequestDTO;
import com.yolt.providers.web.service.ProviderUkDomesticPaymentService;
import com.yolt.providers.web.sitedetails.sites.SiteDetailsService;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.extendeddata.common.CurrencyCode;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.jose4j.jwt.JwtClaims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UkDomesticPaymentProviderConsentUrlRetrieverTest {

    private static final UUID SOME_CLIENT_ID = UUID.randomUUID();
    private static final UUID SOME_CLIENT_GROUP_ID = UUID.randomUUID();
    private static final UUID SOME_CLIENT_REDIRECT_ID = UUID.randomUUID();
    private static final String SOME_URL = "http://somepage.com?redirectUri=https%3A%2F%2Fwww.yolt.com%2Fcallback-dev";
    private static final String SOME_PROVIDER = "SOME_PROVIDER";
    private static final ClientToken SOME_CLIENT_TOKEN = new ClientToken("some_serialized_token", new JwtClaims());
    private static final UUID SOME_SITE_ID = UUID.randomUUID();

    @Mock
    private ProviderUkDomesticPaymentService providerUkDomesticPaymentService;

    @Mock
    private SiteDetailsService siteDetailsService;

    @InjectMocks
    private UkDomesticPaymentProviderConsentUrlRetriever ukDomesticPaymentProviderConsentUrlRetriever;

    @Captor
    private ArgumentCaptor<ExternalInitiateUkScheduledPaymentRequestDTO> initiateUkPaymentRequestDTOArgumentCaptor;

    @Test
    void shouldRetrieveConsentUrlForGivenProviderForClient() {
        // given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(SOME_CLIENT_ID, SOME_CLIENT_REDIRECT_ID);
        when(providerUkDomesticPaymentService.initiateSinglePayment(eq(SOME_PROVIDER), any(ExternalInitiateUkScheduledPaymentRequestDTO.class), eq(SOME_CLIENT_TOKEN), any(UUID.class), eq(false)))
                .thenReturn(new ExternalInitiateUkDomesticPaymentResponseDTO("consentUrl", "someState"));
        when(siteDetailsService.getMatchingSiteIdForProviderKey(anyString())).thenReturn(Optional.of(SOME_SITE_ID.toString()));

        // when
        String result = ukDomesticPaymentProviderConsentUrlRetriever.retrieveConsentUrlForProvider(SOME_PROVIDER, authenticationMeansReference, SOME_URL, SOME_CLIENT_TOKEN);

        // then
        assertThat(result).isEqualTo("consentUrl");
        verify(providerUkDomesticPaymentService).initiateSinglePayment(
                eq("SOME_PROVIDER"),
                initiateUkPaymentRequestDTOArgumentCaptor.capture(),
                eq(SOME_CLIENT_TOKEN),
                eq(SOME_SITE_ID),
                eq(false));
        ExternalInitiateUkScheduledPaymentRequestDTO capturedInitiateUkPaymentRequestDTO = initiateUkPaymentRequestDTOArgumentCaptor.getValue();
        assertThat(capturedInitiateUkPaymentRequestDTO)
                .extracting(ExternalInitiateUkScheduledPaymentRequestDTO::getAuthenticationMeansReference, ExternalInitiateUkScheduledPaymentRequestDTO::getBaseClientRedirectUrl)
                .contains(authenticationMeansReference, SOME_URL);
        assertThat(capturedInitiateUkPaymentRequestDTO.getRequestDTO())
                .isEqualToIgnoringGivenFields(createExpectedInitiateUkDomesticPaymentRequestDTO(), "endToEndIdentification");
    }

    @Test
    void shouldRetrieveConsentUrlForGivenProviderForClientGroup() {
        // given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(null, SOME_CLIENT_GROUP_ID, SOME_CLIENT_REDIRECT_ID);
        when(providerUkDomesticPaymentService.initiateSinglePayment(eq(SOME_PROVIDER), any(ExternalInitiateUkScheduledPaymentRequestDTO.class), eq(SOME_CLIENT_TOKEN), any(UUID.class), eq(false)))
                .thenReturn(new ExternalInitiateUkDomesticPaymentResponseDTO("consentUrl", "someState"));
        when(siteDetailsService.getMatchingSiteIdForProviderKey(anyString())).thenReturn(Optional.of(SOME_SITE_ID.toString()));

        // when
        String result = ukDomesticPaymentProviderConsentUrlRetriever.retrieveConsentUrlForProvider(SOME_PROVIDER, authenticationMeansReference, SOME_URL, SOME_CLIENT_TOKEN);

        // then
        assertThat(result).isEqualTo("consentUrl");
        verify(providerUkDomesticPaymentService).initiateSinglePayment(
                eq(SOME_PROVIDER),
                initiateUkPaymentRequestDTOArgumentCaptor.capture(),
                eq(SOME_CLIENT_TOKEN),
                eq(SOME_SITE_ID),
                eq(false));
        ExternalInitiateUkScheduledPaymentRequestDTO capturedInitiateUkPaymentRequestDTO = initiateUkPaymentRequestDTOArgumentCaptor.getValue();
        assertThat(capturedInitiateUkPaymentRequestDTO)
                .extracting(ExternalInitiateUkScheduledPaymentRequestDTO::getAuthenticationMeansReference, ExternalInitiateUkScheduledPaymentRequestDTO::getBaseClientRedirectUrl)
                .contains(authenticationMeansReference, SOME_URL);
        assertThat(capturedInitiateUkPaymentRequestDTO.getRequestDTO())
                .isEqualToIgnoringGivenFields(createExpectedInitiateUkDomesticPaymentRequestDTO(), "endToEndIdentification");
    }

    @Test
    void shouldReturnTrueForSupportsWhenAppliedProviderOfTypeUkDomesticPaymentProvider() {
        // given
        UkDomesticPaymentProvider ukDomesticPaymentProvider = mock(UkDomesticPaymentProvider.class);

        // when
        boolean result = ukDomesticPaymentProviderConsentUrlRetriever.supports(ukDomesticPaymentProvider);

        // then
        assertThat(result).isTrue();
    }

    private ExternalInitiateUkDomesticScheduledPaymentRequestDTO createExpectedInitiateUkDomesticPaymentRequestDTO() {
        return new ExternalInitiateUkDomesticScheduledPaymentRequestDTO(
                UUID.randomUUID().toString().substring(0, 35),
                CurrencyCode.GBP.name(),
                new BigDecimal("0.01"),
                new UkAccountDTO("20005275849855", AccountIdentifierScheme.SORTCODEACCOUNTNUMBER, "Bogus", null),
                new UkAccountDTO("40051512345674", AccountIdentifierScheme.SORTCODEACCOUNTNUMBER, "Bogus", null),
                "Test UK Domestic payment",
                createExpectedDynamicFields(),
                null
        );
    }

    private Map<String, String> createExpectedDynamicFields() {
        Map<String, String> dynamicFields = new HashMap<>();
        dynamicFields.put("creditorAgentBic", "HBUKGB4B");
        dynamicFields.put("creditorAgentName", "Bogus");
        dynamicFields.put("remittanceInformationStructured", "Test UK Domestic payment");
        return dynamicFields;
    }
}
