package com.yolt.providers.web.service.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.ing.lovebird.providerdomain.ProviderAccountDTO;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
public class IngestionAccountDTO extends ProviderAccountDTO {

    private final UUID yoltUserId;
    private final UUID yoltUserSiteId;
    private final UUID yoltSiteId;
    private final String provider;

    public IngestionAccountDTO(final UUID yoltUserId,
                               final UUID yoltUserSiteId,
                               final UUID yoltSiteId,
                               final String provider,
                               final ProviderAccountDTO providerAccountDTO) {

        super(providerAccountDTO.getYoltAccountType(),
                providerAccountDTO.getLastRefreshed(),
                providerAccountDTO.getAvailableBalance(),
                providerAccountDTO.getCurrentBalance(),
                providerAccountDTO.getAccountId(),
                providerAccountDTO.getAccountMaskedIdentification(),
                providerAccountDTO.getAccountNumber(),
                providerAccountDTO.getBic(),
                providerAccountDTO.getName(),
                providerAccountDTO.getCurrency(),
                providerAccountDTO.getClosed(),
                providerAccountDTO.getCreditCardData(),
                providerAccountDTO.getTransactions(),
                providerAccountDTO.getDirectDebits(),
                providerAccountDTO.getStandingOrders(),
                providerAccountDTO.getExtendedAccount(),
                providerAccountDTO.getBankSpecific(),
                providerAccountDTO.getLinkedAccount());
        this.yoltSiteId = yoltSiteId;
        this.provider = provider;
        this.yoltUserId = yoltUserId;
        this.yoltUserSiteId = yoltUserSiteId;
    }

}
