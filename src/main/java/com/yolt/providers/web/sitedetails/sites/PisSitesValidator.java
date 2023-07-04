package com.yolt.providers.web.sitedetails.sites;

import com.yolt.providers.common.pis.common.PaymentType;
import com.yolt.providers.common.providerdetail.dto.PaymentMethod;
import com.yolt.providers.common.providerdetail.dto.PisSiteDetails;
import com.yolt.providers.web.sitedetails.exceptions.SiteInvalidException;
import lombok.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PisSitesValidator {

    public void validatePisSites(final List<PisSiteDetails> sites) {
        validateAllSitesHaveUniqueIdPaymentTypeAndPaymentMethod(sites);
        validateAllSitesWithSameIdHaveSameName(sites);
    }

    private void validateAllSitesHaveUniqueIdPaymentTypeAndPaymentMethod(List<PisSiteDetails> sites) {

        @Value
        class GroupingTuple {
            UUID id;
            PaymentType paymentType;
            PaymentMethod paymentMethod;
        }
        Set<UUID> idsOfSitesWhichBreakTheRule = sites.stream()
                .map(site -> new GroupingTuple(site.getId(), site.getPaymentType(), site.getPaymentMethod()))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(entry -> entry.getKey().getId())
                .collect(Collectors.toSet());

        if (!idsOfSitesWhichBreakTheRule.isEmpty()) {
            String siteDuplicates = duplicatedIdsToString(sites, idsOfSitesWhichBreakTheRule);
            throw new SiteInvalidException("PisSiteDetails sites should have unique <id, paymentMethod, paymentType> tuple :\n" + siteDuplicates);
        }
    }

    private void validateAllSitesWithSameIdHaveSameName(List<PisSiteDetails> sites) {
        Set<UUID> idsOfSitesWhichBreakTheRule = sites.stream()
                .collect(Collectors.groupingBy(PisSiteDetails::getId))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().size() > 1)
                .filter(entry -> !entry.getValue()
                        .stream()
                        .allMatch(singleEntry -> entry.getValue().get(0).getProviderKey().equals(singleEntry.getProviderKey())))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (!idsOfSitesWhichBreakTheRule.isEmpty()) {
            String siteDuplicates = duplicatedIdsToString(sites, idsOfSitesWhichBreakTheRule);
            throw new SiteInvalidException("PisSiteDetails not all sites have the same name:\n" + siteDuplicates);
        }
    }

    private String duplicatedIdsToString(List<PisSiteDetails> sites, Set<UUID> duplicatedId) {
        return StringUtils.collectionToDelimitedString(sites.stream()
                .filter(site -> duplicatedId.contains(site.getId()))
                .map(site -> siteToString(site))
                .collect(Collectors.toList()), "\n");
    }

    private String siteToString(PisSiteDetails site) {
        return "ID: " + site.getId() + " ProviderKey: " + site.getProviderKey();
    }
}
