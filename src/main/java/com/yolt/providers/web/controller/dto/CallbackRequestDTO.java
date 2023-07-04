package com.yolt.providers.web.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import nl.ing.lovebird.providershared.callback.MoreInfo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@AllArgsConstructor
public class CallbackRequestDTO {

    /**
     * The entire raw payload
     */
    private final String body;

    /**
     * Optional sub-path of received callback
     */
    private final String subPath;

    /**
     * Optional additional information requested by the provider for the callback body
     */
    private final MoreInfo moreInfo;

    /**
     * Required for multi tenancy.
     */
    private final UUID clientId;

    private final List<UserSiteDataFetchInformation> userSiteDataFetchInformation;

    /**
     * We don't know about which usersite the callback is for before the message is parsed. We are sending all the active 'activities' along
     * with this request. We need to know to which activity the refresh belongs to. This identifier is used for further processing of a 'refresh'.
     */
    private final Map<UUID, UUID> userSiteToActiveActivityId;

}
