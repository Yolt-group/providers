package com.yolt.providers.web.sitedetails.dto;

import com.yolt.providers.common.providerdetail.dto.AisSiteDetails;
import com.yolt.providers.common.providerdetail.dto.PisSiteDetails;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@AllArgsConstructor
public class ProvidersSites {
    List<AisSiteDetails> registeredSites;
    List<AisSiteDetails> aisSiteDetails;
    List<PisSiteDetails> pisSiteDetails;
}
