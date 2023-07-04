package com.yolt.providers.web.sitedetails.sites;

import com.yolt.providers.common.providerdetail.AisDetailsProvider;
import com.yolt.providers.common.providerdetail.PisDetailsProvider;
import com.yolt.providers.common.providerdetail.dto.AisSiteDetails;
import com.yolt.providers.common.providerdetail.dto.PisSiteDetails;
import com.yolt.providers.web.sitedetails.dto.AisProviderSiteData;
import com.yolt.providers.web.sitedetails.dto.PisProviderSiteData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SiteDetailsService {

    private final List<AisSiteDetails> aisSiteDetails;
    private final List<PisSiteDetails> pisSiteDetails;

    private final Map<UUID, AisProviderSiteData> aisProviderSitesDataBySiteId;
    private final Map<UUID, PisProviderSiteData> pisProviderSitesDataBySiteId;

    private final Map<String, String> aisSiteIdReverseLookup;

    public SiteDetailsService(List<AisDetailsProvider> aisDetailsProviders,
                              List<PisDetailsProvider> pisDetailsProviders,
                              AisSitesValidator aisSitesValidator,
                              PisSitesValidator pisSitesValidator,
                              CrossSiteValidator crossValidator) {
        this.aisProviderSitesDataBySiteId = new HashMap<>();
        this.aisSiteDetails = new ArrayList<>();
        for (AisDetailsProvider detailsProvider : aisDetailsProviders) {
            aisSiteDetails.addAll(detailsProvider.getAisSiteDetails());

            if (isScraperProvider(detailsProvider)) {
                aisProviderSitesDataBySiteId.putAll(
                        groupByProvider(detailsProvider.getAisSiteDetails()));
            } else {
                //all sites Ids are the same across all sites within one provider so we can take the first one
                UUID currentSiteId = detailsProvider.getAisSiteDetails().get(0).getId();
                aisProviderSitesDataBySiteId.put(currentSiteId, new AisProviderSiteData(detailsProvider.getAisSiteDetails()));
            }
        }
        aisSitesValidator.validateAisSites(aisSiteDetails);

        this.pisSiteDetails = new ArrayList<>();
        this.pisProviderSitesDataBySiteId = new HashMap<>();
        for (PisDetailsProvider details : pisDetailsProviders) {
            //all sites Ids are the same across all PIS sites within one provider so we can take the first one
            UUID currentSiteId = details.getPisSiteDetails().get(0).getId();
            pisSiteDetails.addAll(details.getPisSiteDetails());
            pisProviderSitesDataBySiteId.put(currentSiteId, new PisProviderSiteData(details.getPisSiteDetails()));
        }
        pisSitesValidator.validatePisSites(pisSiteDetails);
        crossValidator.validateUsesStepTypes(aisSiteDetails, pisSiteDetails);
        this.aisSiteIdReverseLookup = prepareReverseSiteIdLookup(aisProviderSitesDataBySiteId);
    }

    public List<AisSiteDetails> getAisSiteDetails() {
        return Collections.unmodifiableList(aisSiteDetails);
    }

    public List<PisSiteDetails> getPisSiteDetails() {
        return Collections.unmodifiableList(pisSiteDetails);
    }

    public Map<UUID, AisProviderSiteData> getAisProviderSitesDataBySiteId() {
        return Collections.unmodifiableMap(aisProviderSitesDataBySiteId);
    }

    public Map<UUID, PisProviderSiteData> getPisProviderSitesDataBySiteId() {
        return Collections.unmodifiableMap(pisProviderSitesDataBySiteId);
    }

    public Optional<String> getMatchingSiteIdForProviderKey(final String providerKey) {
        log.info("Using fallback to fetch siteId via providerKey. Rely just on siteId in the future.");
        return Optional.ofNullable(aisSiteIdReverseLookup.get(providerKey));
    }

    private boolean isScraperProvider(final AisDetailsProvider detailsProvider) {
        //Provider should have the same ProviderKey so we can take the first one
        String providerKey = detailsProvider.getAisSiteDetails().get(0).getProviderKey();
        return providerKey.equals("BUDGET_INSIGHT") || providerKey.equals("YODLEE") || providerKey.equals("SALTEDGE");
    }

    private Map<UUID, AisProviderSiteData> groupByProvider(final List<AisSiteDetails> aisSites) {

        Map<UUID, List<AisSiteDetails>> aisSitesGrouped = aisSites.stream()
                .collect(Collectors.groupingBy(AisSiteDetails::getId));

        Map<UUID, AisProviderSiteData> dataGroupedByProvider = new HashMap<>();
        //go through AIS sites, create grouped records
        aisSitesGrouped.forEach((uuid, data) ->
                dataGroupedByProvider.put(uuid, new AisProviderSiteData(data))
        );

        return dataGroupedByProvider;
    }

    private static Map<String, String> prepareReverseSiteIdLookup(Map<UUID, AisProviderSiteData> aisProviderSitesDataBySiteId) {
        return aisProviderSitesDataBySiteId
                .values()
                .stream()
                .map(AisProviderSiteData::getAisSiteDetails)
                .flatMap(List::stream)
                .filter(aisSiteDetails -> aisSiteDetails.getId() != null)
                .filter((aisSiteDetails1 -> {
                    var providerKey = aisSiteDetails1.getProviderKey();
                    return !aisSiteDetails1.isTestSite() &&
                            !(providerKey.equals("BUDGET_INSIGHT") || providerKey.equals("YODLEE") || providerKey.equals("SALTEDGE"));
                }))
                .collect(Collectors.toUnmodifiableMap(
                        AisSiteDetails::getProviderKey,
                        (aisSiteDetails) -> aisSiteDetails.getId().toString()));
    }
}