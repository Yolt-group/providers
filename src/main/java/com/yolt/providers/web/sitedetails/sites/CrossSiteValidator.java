package com.yolt.providers.web.sitedetails.sites;

import com.yolt.providers.common.providerdetail.dto.AisSiteDetails;
import com.yolt.providers.common.providerdetail.dto.LoginRequirement;
import com.yolt.providers.common.providerdetail.dto.PisSiteDetails;
import com.yolt.providers.web.sitedetails.exceptions.SiteInvalidException;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

@Service
public class CrossSiteValidator {

    public void validateUsesStepTypes(List<AisSiteDetails> aisSiteDetails, List<PisSiteDetails> pisSiteDetails) {
        validateIfUsesStepTypesMatchesLoginRequirementsForAisDetails(aisSiteDetails);
        validateIfUsesStepTypesMatchesLoginRequirementsForPisDetails(aisSiteDetails, pisSiteDetails);
    }

    private void validateIfUsesStepTypesMatchesLoginRequirementsForPisDetails(List<AisSiteDetails> aisSiteDetails, List<PisSiteDetails> pisSiteDetails) {
        Map<UUID, List<LoginRequirement>> loginRequirementsFromAisStepTypes = aisSiteDetails.stream()
                .filter(detail -> detail.getUsesStepTypes().containsKey(ServiceType.PIS))
                .collect(toMap(detail -> detail.getId(), detail -> detail.getUsesStepTypes().get(ServiceType.PIS)));
        Set<UUID> idsThatAreMissingInAisStepTypes = pisSiteDetails.stream()
                .filter(detail -> !loginRequirementsFromAisStepTypes.containsKey(detail.getId()))
                .map(PisSiteDetails::getId)
                .collect(Collectors.toSet());

        if (!idsThatAreMissingInAisStepTypes.isEmpty()) {
            throw new SiteInvalidException("pisSiteDetail is missing entry in usesStepTypes in aisSiteDetails site ids: " +
                    StringUtils.collectionToDelimitedString(idsThatAreMissingInAisStepTypes, "\n"));
        }

        Set<UUID> idsOfDetailsThatDoNotMatchWithStepTypesDefinedInAis = pisSiteDetails.stream()
                .filter(detail -> !loginRequirementsFromAisStepTypes.get(detail.getId()).containsAll(detail.getLoginRequirements())
                        || !detail.getLoginRequirements().containsAll(loginRequirementsFromAisStepTypes.get(detail.getId())))
                .map(PisSiteDetails::getId)
                .collect(Collectors.toSet());

        if (!idsOfDetailsThatDoNotMatchWithStepTypesDefinedInAis.isEmpty()) {
            throw new SiteInvalidException("pisSiteDetail loginRequirements do not match stepTypes in aisDetails: " +
                    StringUtils.collectionToDelimitedString(idsOfDetailsThatDoNotMatchWithStepTypesDefinedInAis, "\n"));
        }
    }


    private void validateIfUsesStepTypesMatchesLoginRequirementsForAisDetails(List<AisSiteDetails> aisSiteDetails) {
        Set<UUID> idsThatHaveNoLoginRequirements = aisSiteDetails.stream()
                .filter(detail -> detail.getLoginRequirements() == null)
                .map(AisSiteDetails::getId)
                .collect(Collectors.toSet());

        if (!idsThatHaveNoLoginRequirements.isEmpty()) {
            throw new SiteInvalidException("aisSiteDetail loginRequirements does not exist: " +
                    StringUtils.collectionToDelimitedString(idsThatHaveNoLoginRequirements, "\n"));
        }

        Set<UUID> siteIdsThatBreakTheRule = aisSiteDetails.stream()
                .filter(detail -> detail.getUsesStepTypes() != null)
                .filter(detail -> !detail.getUsesStepTypes().get(ServiceType.AIS).containsAll(detail.getLoginRequirements())
                        || !detail.getLoginRequirements().containsAll(detail.getUsesStepTypes().get(ServiceType.AIS)))
                .map(AisSiteDetails::getId)
                .collect(Collectors.toSet());

        if (!siteIdsThatBreakTheRule.isEmpty()) {
            throw new SiteInvalidException("aisSiteDetail loginRequirements does not match stepTypes: " +
                    StringUtils.collectionToDelimitedString(siteIdsThatBreakTheRule, "\n"));
        }
    }
}
