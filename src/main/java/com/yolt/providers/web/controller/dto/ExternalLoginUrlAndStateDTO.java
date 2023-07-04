package com.yolt.providers.web.controller.dto;

import com.yolt.providers.common.pis.paymentexecutioncontext.model.PaymentExecutionContextMetadata;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.lang.Nullable;

import javax.validation.constraints.NotNull;

@Getter
@AllArgsConstructor
public class ExternalLoginUrlAndStateDTO {

    @NotNull
    private String loginUrl;
    @NotNull
    private String providerState;
    @Nullable
    private PaymentExecutionContextMetadata paymentExecutionContextMetadata;

    public ExternalLoginUrlAndStateDTO(@NotNull String loginUrl, @Nullable String providerState) {
        this.loginUrl = loginUrl;
        this.providerState = providerState;
    }
}
