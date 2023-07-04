package com.yolt.providers.web.controller.dto;

import com.yolt.providers.common.pis.sepa.DynamicFields;
import com.yolt.providers.common.pis.sepa.InstructionPriority;
import com.yolt.providers.common.pis.sepa.SepaAccountDTO;
import com.yolt.providers.common.pis.sepa.SepaAmountDTO;
import lombok.Builder;
import lombok.Data;
import org.springframework.lang.Nullable;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDate;

@Data
@Builder(toBuilder = true)
public class ExternalSepaInitiatePaymentRequestDTO {

    @NotNull
    private final SepaAccountDTO creditorAccount;

    @NotNull
    @Size(min = 2, max = 70)
    private final String creditorName;

    @NotNull
    @Pattern(regexp = "[A-Za-z0-9-]{1,35}")
    private final String endToEndIdentification;

    @NotNull
    private final SepaAmountDTO instructedAmount;

    @NotNull
    @Size(max = 140)
    private final String remittanceInformationUnstructured;

    /**
     * Optional fields below.
     * The siteslist should indicate whether these properties are supported and/or required.
     */

    @Nullable
    private final SepaAccountDTO debtorAccount;
    @Nullable
    private LocalDate executionDate;
    @Nullable
    private InstructionPriority instructionPriority;
    @Nullable
    private DynamicFields dynamicFields;
}
