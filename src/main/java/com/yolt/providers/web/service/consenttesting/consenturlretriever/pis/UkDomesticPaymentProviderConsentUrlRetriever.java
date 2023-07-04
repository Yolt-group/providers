package com.yolt.providers.web.service.consenttesting.consenturlretriever.pis;

import com.yolt.providers.common.pis.ukdomestic.AccountIdentifierScheme;
import com.yolt.providers.common.pis.ukdomestic.UkAccountDTO;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.common.providerinterface.UkDomesticPaymentProvider;
import com.yolt.providers.web.controller.dto.ExternalInitiateUkDomesticPaymentResponseDTO;
import com.yolt.providers.web.controller.dto.ExternalInitiateUkDomesticScheduledPaymentRequestDTO;
import com.yolt.providers.web.controller.dto.ExternalInitiateUkScheduledPaymentRequestDTO;
import com.yolt.providers.web.service.ProviderUkDomesticPaymentService;
import com.yolt.providers.web.service.consenttesting.consenturlretriever.ConsentUrlRetriever;
import com.yolt.providers.web.sitedetails.sites.SiteDetailsService;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.extendeddata.common.CurrencyCode;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class UkDomesticPaymentProviderConsentUrlRetriever extends ConsentUrlRetriever {

    private static final String TEST_PAYMENT_AMOUNT = "0.01";
    private static final String FAKE_HOLDER_NAME = "Bogus";
    private static final String FAKE_CREDITOR_SORTCODE_ACCOUNT_NUMBER = "20005275849855";
    private static final String FAKE_DEBTOR_SORTCODE_ACCOUNT_NUMBER = "40051512345674";
    private static final String FAKE_REMITTANCE_INFO = "Test UK Domestic payment";
    private static final String FAKE_CREDITOR_AGENT_BIC = "HBUKGB4B";
    private static final String CREDITOR_AGENT_BIC_DYNAMIC_FIELD = "creditorAgentBic";
    private static final String CREDITOR_AGENT_NAME_DYNAMIC_FIELD = "creditorAgentName";
    private static final String REMITTANCE_INFORMATION_STRUCTURED_DYNAMIC_FIELD = "remittanceInformationStructured";

    private final ProviderUkDomesticPaymentService providerUkDomesticPaymentService;
    private final SiteDetailsService siteDetailsService;

    public UkDomesticPaymentProviderConsentUrlRetriever(ProviderUkDomesticPaymentService providerUkDomesticPaymentService, SiteDetailsService siteDetailsService, @Value("${yolt.externalIpAddress}") String externalIpAddress) {
        super(externalIpAddress);
        this.providerUkDomesticPaymentService = providerUkDomesticPaymentService;
        this.siteDetailsService = siteDetailsService;
    }

    @Override
    public String retrieveConsentUrlForProvider(String providerIdentifier, AuthenticationMeansReference authenticationMeansReference, String baseRedirectUrl, ClientToken clientToken) {
        ExternalInitiateUkScheduledPaymentRequestDTO initiateUkPaymentRequestDTO = new ExternalInitiateUkScheduledPaymentRequestDTO(
                createInitiateUkDomesticPaymentRequestDTO(),
                UUID.randomUUID().toString(),
                authenticationMeansReference,
                baseRedirectUrl,
                externalIpAddress
        );
        var siteId = siteDetailsService.getMatchingSiteIdForProviderKey(providerIdentifier)
                .map(UUID::fromString)
                .orElse(FAKE_SITE_ID);
        ExternalInitiateUkDomesticPaymentResponseDTO initiateUkDomesticPaymentResponseDTO = providerUkDomesticPaymentService.initiateSinglePayment(
                providerIdentifier,
                initiateUkPaymentRequestDTO,
                clientToken,
                siteId,
                false);
        return initiateUkDomesticPaymentResponseDTO.getLoginUrl();
    }

    private ExternalInitiateUkDomesticScheduledPaymentRequestDTO createInitiateUkDomesticPaymentRequestDTO() {
        return new ExternalInitiateUkDomesticScheduledPaymentRequestDTO(
                UUID.randomUUID().toString().substring(0, 35),
                CurrencyCode.GBP.name(),
                new BigDecimal(TEST_PAYMENT_AMOUNT),
                new UkAccountDTO(FAKE_CREDITOR_SORTCODE_ACCOUNT_NUMBER, AccountIdentifierScheme.SORTCODEACCOUNTNUMBER, FAKE_HOLDER_NAME, null),
                new UkAccountDTO(FAKE_DEBTOR_SORTCODE_ACCOUNT_NUMBER, AccountIdentifierScheme.SORTCODEACCOUNTNUMBER, FAKE_HOLDER_NAME, null),
                FAKE_REMITTANCE_INFO,
                createDynamicFields(),
                null
        );
    }

    private Map<String, String> createDynamicFields() {
        Map<String, String> dynamicFields = new HashMap<>();
        dynamicFields.put(CREDITOR_AGENT_BIC_DYNAMIC_FIELD, FAKE_CREDITOR_AGENT_BIC);
        dynamicFields.put(CREDITOR_AGENT_NAME_DYNAMIC_FIELD, FAKE_HOLDER_NAME);
        dynamicFields.put(REMITTANCE_INFORMATION_STRUCTURED_DYNAMIC_FIELD, FAKE_REMITTANCE_INFO);
        return dynamicFields;
    }

    @Override
    public boolean supports(Provider provider) {
        return provider instanceof UkDomesticPaymentProvider;
    }

    @Override
    public int getPriority() {
        return HIGHEST_PRIORITY - 1;
    }
}
