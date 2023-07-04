package com.yolt.providers.web.service.consenttesting.consenturlretriever.pis;

import com.yolt.providers.common.pis.sepa.DynamicFields;
import com.yolt.providers.common.pis.sepa.InstructionPriority;
import com.yolt.providers.common.pis.sepa.SepaAccountDTO;
import com.yolt.providers.common.pis.sepa.SepaAmountDTO;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.common.providerinterface.SepaPaymentProvider;
import com.yolt.providers.web.controller.dto.ExternalInitiateSepaPaymentRequestDTO;
import com.yolt.providers.web.controller.dto.ExternalLoginUrlAndStateDTO;
import com.yolt.providers.web.controller.dto.ExternalSepaInitiatePaymentRequestDTO;
import com.yolt.providers.web.service.ProviderSepaPaymentService;
import com.yolt.providers.web.service.consenttesting.consenturlretriever.ConsentUrlRetriever;
import com.yolt.providers.web.sitedetails.sites.SiteDetailsService;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.extendeddata.common.CurrencyCode;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Slf4j
public class SepaPaymentProviderConsentUrlRetriever extends ConsentUrlRetriever {

    private static final String FAKE_HOLDER_NAME = "Bogus";
    private static final String FAKE_CREDITOR_IBAN = "NL52ABNA6512284550";
    private static final String FAKE_DEBTOR_IBAN = "NL32ABNA7507044742";
    private static final String TEST_PAYMENT_AMOUNT = "0.01";
    private static final String FAKE_REMITTANCE_INFO = "Test SEPA payment";
    private static final String FAKE_CREDITOR_AGENT_BIC = "ABNANL2A";
    private static final String FAKE_CREDITOR_POSTAL_COUNTRY_CODE = "NL";
    private static final String FAKE_CREDITOR_POSTAL_ADDRESS_LINE = "Dam, 1012 JL Amsterdam";

    private final ProviderSepaPaymentService providerSepaPaymentService;
    private final SiteDetailsService siteDetailsService;

    public SepaPaymentProviderConsentUrlRetriever(ProviderSepaPaymentService providerSepaPaymentService,
                                                  SiteDetailsService siteDetailsService, @Value("${yolt.externalIpAddress}") String externalIpAddress) {
        super(externalIpAddress);
        this.providerSepaPaymentService = providerSepaPaymentService;
        this.siteDetailsService = siteDetailsService;
    }

    @Override
    public String retrieveConsentUrlForProvider(String providerIdentifier, AuthenticationMeansReference authenticationMeansReference, String baseRedirectUrl, ClientToken clientToken) {
        ExternalInitiateSepaPaymentRequestDTO initiateSepaPaymentRequestDTO = new ExternalInitiateSepaPaymentRequestDTO(
                prepareSepaInitiatePaymentRequestDTO(),
                UUID.randomUUID().toString(),
                authenticationMeansReference,
                baseRedirectUrl,
                externalIpAddress
        );

        var siteId = siteDetailsService.getMatchingSiteIdForProviderKey(providerIdentifier)
                .map(UUID::fromString)
                .orElse(FAKE_SITE_ID);

        ExternalLoginUrlAndStateDTO loginUrlAndStateDTO = providerSepaPaymentService.initiateSinglePayment(providerIdentifier,
                initiateSepaPaymentRequestDTO,
                clientToken,
                siteId,
                false);

        return loginUrlAndStateDTO.getLoginUrl();
    }

    private ExternalSepaInitiatePaymentRequestDTO prepareSepaInitiatePaymentRequestDTO() {
        return ExternalSepaInitiatePaymentRequestDTO.builder()
                .creditorName(FAKE_HOLDER_NAME)
                .creditorAccount(SepaAccountDTO.builder()
                        .currency(CurrencyCode.EUR)
                        .iban(FAKE_CREDITOR_IBAN)
                        .build())
                .debtorAccount(SepaAccountDTO.builder()
                        .currency(CurrencyCode.EUR)
                        .iban(FAKE_DEBTOR_IBAN)
                        .build())
                .endToEndIdentification(UUID.randomUUID().toString().substring(0, 35))
                .instructedAmount(SepaAmountDTO.builder()
                        .amount(new BigDecimal(TEST_PAYMENT_AMOUNT))
                        .build())
                .instructionPriority(InstructionPriority.NORMAL)
                .remittanceInformationUnstructured(FAKE_REMITTANCE_INFO)
                .dynamicFields(prepareDynamicFields())
                .build();
    }

    private DynamicFields prepareDynamicFields() {
        DynamicFields dynamicFields = new DynamicFields();
        dynamicFields.setDebtorName(FAKE_HOLDER_NAME);
        dynamicFields.setCreditorAgentBic(FAKE_CREDITOR_AGENT_BIC);
        dynamicFields.setCreditorAgentName(FAKE_HOLDER_NAME);
        dynamicFields.setCreditorPostalCountry(FAKE_CREDITOR_POSTAL_COUNTRY_CODE);
        dynamicFields.setCreditorPostalAddressLine(FAKE_CREDITOR_POSTAL_ADDRESS_LINE);
        dynamicFields.setRemittanceInformationStructured(FAKE_REMITTANCE_INFO);
        return dynamicFields;
    }

    @Override
    public boolean supports(Provider provider) {
        return provider instanceof SepaPaymentProvider;
    }

    @Override
    public int getPriority() {
        return HIGHEST_PRIORITY;
    }
}
