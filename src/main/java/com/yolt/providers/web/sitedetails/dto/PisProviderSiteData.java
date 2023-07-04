package com.yolt.providers.web.sitedetails.dto;

import com.yolt.providers.common.providerdetail.dto.PisSiteDetails;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@AllArgsConstructor
public class PisProviderSiteData {

    List<PisSiteDetails> pisSiteDetails;
}
