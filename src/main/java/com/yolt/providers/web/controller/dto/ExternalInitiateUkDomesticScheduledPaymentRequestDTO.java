package com.yolt.providers.web.controller.dto;

import com.yolt.providers.common.pis.ukdomestic.InitiateUkDomesticPaymentRequestDTO;
import com.yolt.providers.common.pis.ukdomestic.UkAccountDTO;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.lang.Nullable;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.beans.ConstructorProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Value
@EqualsAndHashCode(callSuper = true)
public class ExternalInitiateUkDomesticScheduledPaymentRequestDTO extends InitiateUkDomesticPaymentRequestDTO {

    @Nullable
    LocalDate executionDate;

    @ConstructorProperties({"endToEndIdentification", "currencyCode", "amount", "creditorAccount", "debtorAccount", "remittanceInformationUnstructured", "dynamicFields", "executionDate"})
    public ExternalInitiateUkDomesticScheduledPaymentRequestDTO(@NotNull String endToEndIdentification,
                                                                @NotNull String currencyCode,
                                                                @NotNull BigDecimal amount,
                                                                @NotNull UkAccountDTO creditorAccount,
                                                                @Valid UkAccountDTO debtorAccount,
                                                                String remittanceInformationUnstructured,
                                                                Map<String, String> dynamicFields,
                                                                @NotNull LocalDate executionDate) {
        super(endToEndIdentification, currencyCode, amount, creditorAccount, debtorAccount, remittanceInformationUnstructured, dynamicFields);
        this.executionDate = executionDate;
    }
}