package com.yolt.providers.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.ais.DataProviderResponse;
import com.yolt.providers.common.ais.form.FormUserSiteDataProviderResponse;
import com.yolt.providers.common.exception.ExternalUserSiteDoesNotExistException;
import com.yolt.providers.web.controller.dto.UserSiteDataFetchInformation;
import com.yolt.providers.web.service.domain.ProviderAccountUtil;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.providerdomain.AccountType;
import nl.ing.lovebird.providerdomain.ProviderAccountDTO;
import nl.ing.lovebird.providershared.callback.UserDataCallbackResponse;
import nl.ing.lovebird.providershared.callback.UserSiteData;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.yolt.providers.common.ais.form.FormUserSiteDataProviderResponse.Status;
import static com.yolt.providers.common.ais.form.FormUserSiteDataProviderResponse.Status.FINISHED;
import static com.yolt.providers.common.ais.form.FormUserSiteDataProviderResponse.Status.REFRESH_IN_PROGRESS_WAITING_CALLBACK;
import static com.yolt.providers.web.configuration.ApplicationConfiguration.OBJECT_MAPPER;

@Slf4j
@Service
public class AccountsFilterService {
    private final ErrorTopicProducer errorTopicProducer;
    private final ObjectMapper objectMapper;
    private final ProviderAccountUtil providerAccountUtil;
    private final MeterRegistry registry;

    public AccountsFilterService(final ErrorTopicProducer errorTopicProducer,
                                 @Qualifier(OBJECT_MAPPER) final ObjectMapper objectMapper,
                                 final ProviderAccountUtil providerAccountUtil,
                                 final MeterRegistry registry) {
        this.errorTopicProducer = errorTopicProducer;
        this.objectMapper = objectMapper;
        this.providerAccountUtil = providerAccountUtil;
        this.registry = registry;
    }

    public UserDataCallbackResponse filterCallbackResponse(final String providerName, final UserDataCallbackResponse response, final List<UserSiteDataFetchInformation> userSitesDataFetchInformation, UUID userId) throws ExternalUserSiteDoesNotExistException {

        final UserDataCallbackResponse unfilteredResponse = response;
        final UserSiteData userSiteData = unfilteredResponse.getUserSiteData();
        final String externalUserSiteId = userSiteData.getExternalUserSiteId();
        final UserSiteDataFetchInformation userSiteDataFetchInformation = userSitesDataFetchInformation.stream()
                .filter(data -> Objects.equals(data.getUserSiteExternalId(), externalUserSiteId))
                .findFirst()
                .orElseThrow(
                        () -> new ExternalUserSiteDoesNotExistException(
                                String.format("Can't find UserSite for provider %s with external id %s, provider response status %s. For BudgetInsight it is know issue", providerName, externalUserSiteId, userSiteData.getProviderServiceResponseStatusValue().toString())
                        ));

        final List<ProviderAccountDTO> filteredAccounts = doFilter(providerName, userSiteData.getAccounts(), userSiteDataFetchInformation, userId);

        return new UserDataCallbackResponse(
                unfilteredResponse.getProvider(),
                unfilteredResponse.getExternalUserId(),
                new UserSiteData(
                        externalUserSiteId,
                        filteredAccounts,
                        userSiteData.getFailedAccounts(),
                        userSiteData.getProviderServiceResponseStatusValue())
        );
    }

    public DataProviderResponse filterNormalResponse(String providerName, DataProviderResponse response, UserSiteDataFetchInformation userSiteDataFetchInformation, UUID userId) {
        final List<ProviderAccountDTO> unfilteredAccounts = response.getAccounts();

        final List<ProviderAccountDTO> filteredAccounts = doFilter(providerName, unfilteredAccounts, userSiteDataFetchInformation, userId);

        if (response instanceof FormUserSiteDataProviderResponse) {
            Status status = response.isDataExpectedAsynchronousViaCallback() ? REFRESH_IN_PROGRESS_WAITING_CALLBACK : FINISHED;
            return new FormUserSiteDataProviderResponse(
                    filteredAccounts,
                    status);
        } else {
            return new DataProviderResponse(
                    filteredAccounts
            );
        }
    }

    @SneakyThrows
    private List<ProviderAccountDTO> doFilter(String providerName, List<ProviderAccountDTO> unfilteredAccounts,
                                              UserSiteDataFetchInformation userSiteDataFetchInformation, UUID userId) {
        final List<String> migratedAccountsExternalIds = userSiteDataFetchInformation.getUserSiteMigratedAccountIds();
        final List<AccountType> siteWhiteListedAccountType = userSiteDataFetchInformation.getSiteWhiteListedAccountType();
        List<ProviderAccountDTO> filteredAccounts = new ArrayList<>();
        ProviderAccountDTO filteredAccount;
        for (ProviderAccountDTO account : unfilteredAccounts) {
            try {
                filteredAccount = providerAccountUtil.validateAndFilterOptionalDetails(account);

                if (siteWhiteListedAccountType.contains(account.getYoltAccountType())
                        && !migratedAccountsExternalIds.contains(account.getAccountId())) {
                    filteredAccounts.add(filteredAccount);
                }

                // This metric was added at the request of the YTS Core team.  The 'account migration' functionality
                // is deprecated and we are interested in knowing if we could potentially drop the userSiteMigratedAccountIds
                // field from the UserSiteDataFetchInformation object.  To this end we keep track of how often we filter
                // out accounts based on this list.  If the counter remains 0 we can remove this list since it is clearly no
                // longer needed.
                if (migratedAccountsExternalIds.contains(account.getAccountId())) {
                    registry.counter("account_migration_filter", "provider", providerName).increment();
                }
            } catch (NullPointerException ex) {
                log.error("Validation of providerAccountDTO for {} failed, it will be ignored", providerName, ex);
                errorTopicProducer.sendMessage(objectMapper.writeValueAsString(account), userId);
            }
        }
        return filteredAccounts;
    }
}
