package com.yolt.providers.web.sitedetails.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.providerdetail.dto.AisSiteDetails;
import com.yolt.providers.common.providerdetail.dto.PisSiteDetails;
import com.yolt.providers.web.configuration.TestConfiguration;
import com.yolt.providers.web.errorhandling.ExtendedExceptionHandlingService;
import com.yolt.providers.web.sitedetails.dto.AisProviderSiteData;
import com.yolt.providers.web.sitedetails.dto.PisProviderSiteData;
import com.yolt.providers.web.sitedetails.dto.ProvidersSites;
import com.yolt.providers.web.sitedetails.sites.PisSiteDetailsFixture;
import com.yolt.providers.web.sitedetails.sites.SiteDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.yolt.providers.common.providerdetail.dto.ConsentBehavior.CONSENT_PER_ACCOUNT;
import static com.yolt.providers.common.providerdetail.dto.CountryCode.GB;
import static com.yolt.providers.common.providerdetail.dto.LoginRequirement.REDIRECT;
import static com.yolt.providers.common.providerdetail.dto.ProviderBehaviour.STATE;
import static com.yolt.providers.common.providerdetail.dto.ProviderType.DIRECT_CONNECTION;
import static com.yolt.providers.web.configuration.TestConfiguration.JACKSON_OBJECT_MAPPER;
import static java.util.List.of;
import static nl.ing.lovebird.providerdomain.AccountType.CURRENT_ACCOUNT;
import static nl.ing.lovebird.providerdomain.ServiceType.AIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(controllers = SiteDetailsController.class)
@Import({
        TestConfiguration.class,
        ExtendedExceptionHandlingService.class
})
public class SiteDetailsControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    @Qualifier(JACKSON_OBJECT_MAPPER)
    private ObjectMapper objectMapper;
    @MockBean
    private SiteDetailsService siteDetailsService;

    @Test
    void shouldReturnListOfAisSiteDetails() throws Exception {
        //given
        String siteId = UUID.randomUUID().toString();
        AisSiteDetails testSite = AisSiteDetails.site(siteId, "Name", "PROVIDER_KEY", DIRECT_CONNECTION, of(STATE), of(CURRENT_ACCOUNT), of(GB))
                .groupingBy("groupingBy")
                .consentExpiryInDays(90)
                .consentBehavior(Set.of(CONSENT_PER_ACCOUNT))
                .externalId("externalId")
                .usesStepTypes(Map.of(AIS, of(REDIRECT)))
                .loginRequirements(List.of(REDIRECT))
                .build();
        List<AisSiteDetails> list = List.of(testSite);
        when(siteDetailsService.getAisSiteDetails()).thenReturn(list);
        AisProviderSiteData siteData = new AisProviderSiteData(list);
        when(siteDetailsService.getAisProviderSitesDataBySiteId()).thenReturn(Map.of(UUID.fromString(siteId), siteData));
        //when
        MockHttpServletResponse response = mockMvc.perform(get("/sites-details"))
                .andReturn()
                .getResponse();

        //then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        ProvidersSites responseBody = objectMapper.readValue(response.getContentAsString(), ProvidersSites.class);
        assertThat(responseBody.getAisSiteDetails()).hasSize(1);
        assertThat(responseBody.getAisSiteDetails().get(0)).isEqualTo(testSite);
    }

    @Test
    void shouldReturnListOfPisSiteDetails() throws Exception {
        //given
        UUID siteId = UUID.randomUUID();
        PisSiteDetails pisSiteDetail = PisSiteDetailsFixture.getPisSiteDetails(siteId);
        when(siteDetailsService.getPisSiteDetails()).thenReturn(List.of(pisSiteDetail));
        PisProviderSiteData siteData = new PisProviderSiteData(List.of(pisSiteDetail));
        when(siteDetailsService.getPisProviderSitesDataBySiteId()).thenReturn(Map.of(siteId, siteData));
        //when
        MockHttpServletResponse response = mockMvc.perform(get("/sites-details"))
                .andReturn()
                .getResponse();

        //then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        ProvidersSites responseBody = objectMapper.readValue(response.getContentAsString(), ProvidersSites.class);
        assertThat(responseBody.getPisSiteDetails()).hasSize(1);
        assertThat(responseBody.getPisSiteDetails().get(0)).isEqualTo(pisSiteDetail);
    }
}