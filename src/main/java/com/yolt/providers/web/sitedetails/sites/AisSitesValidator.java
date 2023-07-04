package com.yolt.providers.web.sitedetails.sites;

import com.yolt.providers.common.providerdetail.dto.AisSiteDetails;
import com.yolt.providers.common.providerdetail.dto.ProviderType;
import com.yolt.providers.web.sitedetails.exceptions.SiteInvalidException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yolt.providers.common.providerdetail.dto.ProviderType.DIRECT_CONNECTION;

@Service
public class AisSitesValidator {

    //Please think long and hard before adding another exception.  The exceptions encoded in this method existed at the time of writing this test.
    private static final List<String> GROUPING_BY_EXCEPTIONS = List.of(
            "REVOLUT_EU",
            "AMEX_EU",
            // Expected groupingBy "Revolut" only from these sites [3673486a-2ff0-4508-8de1-b8b1fbf213f4] but got it from these too: [555fa50d-62aa-484f-be41-399c510ff375]
            "REVOLUT",
            // Expected groupingBy "American Express Cards" only from these sites [75457be7-96d3-4fc1-98a2-98ded940b563] but got it from these too: [cf4f7f59-a407-4139-ab7a-d37dcd969bcd]
            "AMEX"
    );

    public void validateAisSites(final List<AisSiteDetails> sites) {
        validateIfAllIdsAreUnique(sites);
        validateIfProviderKeysInUrlProvidersAreUnique(sites);
        validateIfAllScrapersHaveExternalId(sites);
        validateIfAllNonScrapersDoNotHaveExternalId(sites);
        validateIfAllSitesHaveConsentBehavior(sites);
        validateOnlyOneGroupingByPerUrlProvider(sites);
    }

    private void validateOnlyOneGroupingByPerUrlProvider(final List<AisSiteDetails> sites) {
        List<UUID> idsOfSitesWithDuplicatedgroupingByPerProviderKey = sites.stream()
                .filter(site -> DIRECT_CONNECTION.equals(site.getProviderType()))
                .filter(site -> !site.isTestSite())
                .filter(site -> !GROUPING_BY_EXCEPTIONS.contains(site.getProviderKey()))
                .filter(site -> site.getGroupingBy() != null)
                .collect(Collectors.groupingBy(AisSiteDetails::getGroupingBy))
                .values()
                .stream()
                .filter(list -> list.size() > 1)
                .flatMap(Collection::stream)
                .map(AisSiteDetails::getId)
                .collect(Collectors.toList());

        vetoIfDuplicatedValuesOccured(sites, idsOfSitesWithDuplicatedgroupingByPerProviderKey, "grouping by");
    }

    private void validateIfProviderKeysInUrlProvidersAreUnique(final List<AisSiteDetails> sites) {
        List<UUID> idsOfSitesWithDuplicatedProviderKeys = sites.stream()
                .filter(site -> DIRECT_CONNECTION.equals(site.getProviderType()))
                .filter(site -> !site.isTestSite())
                .collect(Collectors.groupingBy(AisSiteDetails::getProviderKey))
                .values()
                .stream()
                .filter(list -> list.size() > 1)
                .flatMap(Collection::stream)
                .map(AisSiteDetails::getId)
                .collect(Collectors.toList());

        vetoIfDuplicatedValuesOccured(sites, idsOfSitesWithDuplicatedProviderKeys, "provider key");
    }

    private void validateThatThereIsNoGivenSites(final List<AisSiteDetails> sites, String errorSiteListDescription, Predicate<AisSiteDetails>... predicates) {
        List<UUID> sitesWithoutConsentBehavior = sites.stream()
                .filter(Stream.of(predicates).reduce(Predicate::and).orElse(v -> false))
                .map(AisSiteDetails::getId)
                .collect(Collectors.toList());
        if (!sitesWithoutConsentBehavior.isEmpty()) {
            throw new SiteInvalidException(errorSiteListDescription + ": " + StringUtils.collectionToDelimitedString(sitesWithoutConsentBehavior, ","));
        }
    }

    private void validateIfAllSitesHaveConsentBehavior(final List<AisSiteDetails> sites) {
        validateThatThereIsNoGivenSites(sites,
                "Sites without consent behavior",
                site -> site.getConsentBehavior() == null);
    }

    private void validateIfAllScrapersHaveExternalId(final List<AisSiteDetails> sites) {
        validateThatThereIsNoGivenSites(sites,
                "Scrapers without externalIds",
                site -> ProviderType.SCRAPING.equals(site.getProviderType()),
                site -> site.getExternalId() == null);
    }

    private void validateIfAllNonScrapersDoNotHaveExternalId(final List<AisSiteDetails> sites) {
        validateThatThereIsNoGivenSites(sites,
                "Non scrapers with externalIds",
                site -> ProviderType.DIRECT_CONNECTION.equals(site.getProviderType()),
                site -> site.getExternalId() != null);
    }

    private void validateIfAllIdsAreUnique(final List<AisSiteDetails> sites) {
        List<UUID> idsOfSitesWithDuplicatedIds = sites.stream()
                .collect(Collectors.groupingBy(AisSiteDetails::getId))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        vetoIfDuplicatedValuesOccured(sites, idsOfSitesWithDuplicatedIds, "id");
    }

    private void vetoIfDuplicatedValuesOccured(final List<AisSiteDetails> sites, final List<UUID> idsOfSitesWithDuplicatedIds, String duplicatedVariable) {
        if (!idsOfSitesWithDuplicatedIds.isEmpty()) {
            String siteDuplicates = duplicatedIdsToString(sites, idsOfSitesWithDuplicatedIds);
            throw new SiteInvalidException("Duplicated " + duplicatedVariable + " in site(s):\n" + siteDuplicates);
        }
    }

    private String duplicatedIdsToString(List<AisSiteDetails> sites, List<UUID> duplicatedId) {
        return StringUtils.collectionToDelimitedString(sites.stream()
                .filter(site -> duplicatedId.contains(site.getId()))
                .map(site -> siteToString(site))
                .collect(Collectors.toList()), "\n");
    }

    private String idsToString(Set<UUID> someUUIDS) {
        return StringUtils.collectionToDelimitedString(someUUIDS, "\n");
    }

    private String siteToString(AisSiteDetails site) {
        return "ID: " + site.getId() + " ProviderKey: " + site.getProviderKey();
    }
}
