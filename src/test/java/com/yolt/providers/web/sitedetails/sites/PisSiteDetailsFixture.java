package com.yolt.providers.web.sitedetails.sites;

import com.yolt.providers.common.providerdetail.dto.LoginRequirement;
import com.yolt.providers.common.providerdetail.dto.PaymentMethod;
import com.yolt.providers.common.providerdetail.dto.PisSiteDetails;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.yolt.providers.common.pis.common.PaymentType.SINGLE;
import static com.yolt.providers.common.providerdetail.dto.LoginRequirement.REDIRECT;

public class PisSiteDetailsFixture {
    public static PisSiteDetails getPisSiteDetails(UUID uuid) {
        return PisSiteDetails.builder()
                .id(uuid)
                .providerKey("ProviderKey")
                .supported(true)
                .paymentType(SINGLE)
                .dynamicFields(Map.of())
                .requiresSubmitStep(false)
                .paymentMethod(PaymentMethod.SEPA)
                .loginRequirements(List.of(REDIRECT))
                .build();
    }

    public static PisSiteDetails getPisSiteDetailsSepa(UUID uuid, String providerKey) {
        return PisSiteDetails.builder()
                .id(uuid)
                .providerKey(providerKey)
                .supported(true)
                .paymentType(SINGLE)
                .dynamicFields(Map.of())
                .requiresSubmitStep(false)
                .paymentMethod(PaymentMethod.SEPA)
                .loginRequirements(List.of(REDIRECT))
                .build();
    }

    public static PisSiteDetails getPisSiteDetailsUKDomestic(UUID uuid, String providerKey) {
        return PisSiteDetails.builder()
                .id(uuid)
                .providerKey(providerKey)
                .supported(true)
                .paymentType(SINGLE)
                .dynamicFields(Map.of())
                .requiresSubmitStep(false)
                .paymentMethod(PaymentMethod.UKDOMESTIC)
                .loginRequirements(List.of(REDIRECT))
                .build();
    }

    public static PisSiteDetails getPisSiteDetails(UUID uuid, LoginRequirement loginRequirement) {
        return PisSiteDetails.builder()
                .id(uuid)
                .providerKey("ProviderKey")
                .supported(true)
                .paymentType(SINGLE)
                .dynamicFields(Map.of())
                .requiresSubmitStep(false)
                .paymentMethod(PaymentMethod.UKDOMESTIC)
                .loginRequirements(List.of(loginRequirement))
                .build();
    }
}
