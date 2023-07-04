package com.yolt.providers.web.service.consenttesting.consenturlretriever.pis;

import com.yolt.providers.common.pis.sepa.DynamicFields;
import com.yolt.providers.common.pis.sepa.InstructionPriority;
import com.yolt.providers.common.pis.sepa.SepaAccountDTO;
import com.yolt.providers.common.pis.sepa.SepaAmountDTO;
import com.yolt.providers.common.providerinterface.SepaPaymentProvider;
import com.yolt.providers.web.controller.dto.ExternalInitiateSepaPaymentRequestDTO;
import com.yolt.providers.web.controller.dto.ExternalLoginUrlAndStateDTO;
import com.yolt.providers.web.controller.dto.ExternalSepaInitiatePaymentRequestDTO;
import com.yolt.providers.web.service.ProviderSepaPaymentService;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SepaPaymentProviderConsentUrlRetrieverTest {

    private static final UUID SOME_CLIENT_ID = UUID.randomUUID();
    private static final UUID SOME_CLIENT_GROUP_ID = UUID.randomUUID();
    private static final UUID SOME_CLIENT_REDIRECT_ID = UUID.randomUUID();
    private static final String SOME_URL = "http://somepage.com?redirectUri=https%3A%2F%2Fwww.yolt.com%2Fcallback-dev";
    private static final String SOME_PROVIDER = "SOME_PROVIDER";
    private static final ClientToken SOME_CLIENT_TOKEN = new ClientToken("some_serialized_token", new JwtClaims());
    private static final UUID SOME_SITE_ID = UUID.randomUUID();

    @Mock
    private ProviderSepaPaymentService providerSepaPaymentService;

    @Mock
    private SiteDetailsService siteDetailsService;

    @InjectMocks
    private SepaPaymentProviderConsentUrlRetriever sepaPaymentProviderConsentUrlRetriever;

    @Captor
    private ArgumentCaptor<ExternalInitiateSepaPaymentRequestDTO> initiateSepaPaymentRequestDTOArgumentCaptor;

    @Test
    void shouldRetrieveConsentUrlForGivenProviderForClient() {
        // given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(SOME_CLIENT_ID, SOME_CLIENT_REDIRECT_ID);
        when(providerSepaPaymentService.initiateSinglePayment(eq(SOME_PROVIDER), any(ExternalInitiateSepaPaymentRequestDTO.class), eq(SOME_CLIENT_TOKEN), any(UUID.class), eq(false)))
                .thenReturn(new ExternalLoginUrlAndStateDTO("consentUrl", "someState"));
        when(siteDetailsService.getMatchingSiteIdForProviderKey(anyString())).thenReturn(Optional.of(SOME_SITE_ID.toString()));

        // when
        String result = sepaPaymentProviderConsentUrlRetriever.retrieveConsentUrlForProvider(SOME_PROVIDER, authenticationMeansReference, SOME_URL, SOME_CLIENT_TOKEN);

        // then
        assertThat(result).isEqualTo("consentUrl");
        verify(providerSepaPaymentService).initiateSinglePayment(eq(SOME_PROVIDER),
                initiateSepaPaymentRequestDTOArgumentCaptor.capture(),
                eq(SOME_CLIENT_TOKEN),
                eq(SOME_SITE_ID),
                eq(false));
        ExternalInitiateSepaPaymentRequestDTO capturedInitiateSepaPaymentRequestDTO = initiateSepaPaymentRequestDTOArgumentCaptor.getValue();
        assertThat(capturedInitiateSepaPaymentRequestDTO)
                .extracting(ExternalInitiateSepaPaymentRequestDTO::getAuthenticationMeansReference, ExternalInitiateSepaPaymentRequestDTO::getBaseClientRedirectUrl)
                .contains(authenticationMeansReference, SOME_URL);
        assertThat(capturedInitiateSepaPaymentRequestDTO.getRequestDTO())
                .isEqualToIgnoringGivenFields(prepareExpectedSepaInitiatePaymentRequestDTO(), "endToEndIdentification");
    }

    @Test
    void shouldRetrieveConsentUrlForGivenProviderForClientGroup() {
        // given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(null, SOME_CLIENT_GROUP_ID, SOME_CLIENT_REDIRECT_ID);
        when(providerSepaPaymentService.initiateSinglePayment(eq(SOME_PROVIDER), any(ExternalInitiateSepaPaymentRequestDTO.class), eq(SOME_CLIENT_TOKEN), any(UUID.class), eq(false)))
                .thenReturn(new ExternalLoginUrlAndStateDTO("consentUrl", "someState"));
        when(siteDetailsService.getMatchingSiteIdForProviderKey(anyString())).thenReturn(Optional.of(SOME_SITE_ID.toString()));

        // when
        String result = sepaPaymentProviderConsentUrlRetriever.retrieveConsentUrlForProvider(SOME_PROVIDER, authenticationMeansReference, SOME_URL, SOME_CLIENT_TOKEN);

        // then
        assertThat(result).isEqualTo("consentUrl");
        verify(providerSepaPaymentService).initiateSinglePayment(eq(SOME_PROVIDER),
                initiateSepaPaymentRequestDTOArgumentCaptor.capture(),
                eq(SOME_CLIENT_TOKEN),
                eq(SOME_SITE_ID),
                eq(false));
        ExternalInitiateSepaPaymentRequestDTO capturedInitiateSepaPaymentRequestDTO = initiateSepaPaymentRequestDTOArgumentCaptor.getValue();
        assertThat(capturedInitiateSepaPaymentRequestDTO)
                .extracting(ExternalInitiateSepaPaymentRequestDTO::getAuthenticationMeansReference, ExternalInitiateSepaPaymentRequestDTO::getBaseClientRedirectUrl)
                .contains(authenticationMeansReference, SOME_URL);
        assertThat(capturedInitiateSepaPaymentRequestDTO.getRequestDTO())
                .isEqualToIgnoringGivenFields(prepareExpectedSepaInitiatePaymentRequestDTO(), "endToEndIdentification");
    }

    @Test
    void shouldReturnTrueForSupportsWhenAppliedProviderOfTypeSepaPaymentProvider() {
        // given
        SepaPaymentProvider urlDataProvider = mock(SepaPaymentProvider.class);

        // when
        boolean result = sepaPaymentProviderConsentUrlRetriever.supports(urlDataProvider);

        // then
        assertThat(result).isTrue();
    }

    private ExternalSepaInitiatePaymentRequestDTO prepareExpectedSepaInitiatePaymentRequestDTO() {
        return ExternalSepaInitiatePaymentRequestDTO.builder()
                .creditorName("Bogus")
                .creditorAccount(SepaAccountDTO.builder()
                        .currency(CurrencyCode.EUR)
                        .iban("NL52ABNA6512284550")
                        .build())
                .debtorAccount(SepaAccountDTO.builder()
                        .currency(CurrencyCode.EUR)
                        .iban("NL32ABNA7507044742")
                        .build())
                .endToEndIdentification(UUID.randomUUID().toString().substring(0, 35))
                .instructedAmount(SepaAmountDTO.builder()
                        .amount(new BigDecimal("0.01"))
                        .build())
                .instructionPriority(InstructionPriority.NORMAL)
                .remittanceInformationUnstructured("Test SEPA payment")
                .dynamicFields(prepareExpectedDynamicFields())
                .build();
    }

    private DynamicFields prepareExpectedDynamicFields() {
        DynamicFields dynamicFields = new DynamicFields();
        dynamicFields.setDebtorName("Bogus");
        dynamicFields.setCreditorAgentBic("ABNANL2A");
        dynamicFields.setCreditorAgentName("Bogus");
        dynamicFields.setCreditorPostalCountry("NL");
        dynamicFields.setCreditorPostalAddressLine("Dam, 1012 JL Amsterdam");
        dynamicFields.setRemittanceInformationStructured("Test SEPA payment");
        return dynamicFields;
    }
}
