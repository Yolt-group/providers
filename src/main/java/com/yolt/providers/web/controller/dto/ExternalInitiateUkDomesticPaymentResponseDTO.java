package com.yolt.providers.web.controller.dto;

import com.yolt.providers.common.pis.paymentexecutioncontext.model.PaymentExecutionContextMetadata;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.lang.Nullable;

import javax.validation.constraints.NotNull;

@Getter
@AllArgsConstructor
public class ExternalInitiateUkDomesticPaymentResponseDTO {

    @NotNull
    private String loginUrl;
    @NotNull
    private String providerState;
    @Nullable
    private PaymentExecutionContextMetadata paymentExecutionContextMetadata;

    public ExternalInitiateUkDomesticPaymentResponseDTO(@NotNull String loginUrl, String providerState) {
        this.loginUrl = loginUrl;
        this.providerState = providerState;
    }
}