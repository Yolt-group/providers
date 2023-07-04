package com.yolt.providers.web.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import nl.ing.lovebird.providerdomain.AccountType;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class UserSiteDataFetchInformation {
    private final String userSiteExternalId;
    private final UUID userSiteId;
    private final UUID siteId;
    private final List<String> userSiteMigratedAccountIds;
    private final List<AccountType> siteWhiteListedAccountType;
}
