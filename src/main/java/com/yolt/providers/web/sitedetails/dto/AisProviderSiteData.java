package com.yolt.providers.web.sitedetails.dto;

import com.yolt.providers.common.providerdetail.dto.AisSiteDetails;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@AllArgsConstructor
public class AisProviderSiteData {

    List<AisSiteDetails> aisSiteDetails;
}
