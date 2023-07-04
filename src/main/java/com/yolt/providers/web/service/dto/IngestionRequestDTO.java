package com.yolt.providers.web.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.UUID;

@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class IngestionRequestDTO {
    private UUID activityId;
    private List<IngestionAccountDTO> ingestionAccounts;
    private UUID userSiteId;
    private UUID siteId;
}
