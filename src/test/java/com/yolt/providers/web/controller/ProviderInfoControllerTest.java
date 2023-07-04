package com.yolt.providers.web.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.web.configuration.TestConfiguration;
import com.yolt.providers.web.service.ProviderInfoService;
import com.yolt.providers.web.service.domain.ProviderInfo;
import com.yolt.providers.web.service.domain.ServiceInfo;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ExtendWith(MockitoExtension.class)
public class ProviderInfoControllerTest {

    public static final String TEST_IMPL_OPENBANKING_NAME = "TEST_IMPL_OPENBANKING";
    public static final String TEST_IMPL_OPENBANKING_DISPLAY_NAME = "Test Impl OpenBanking";
    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new TestConfiguration().jacksonObjectMapper();
    private Map<String, ProviderInfo> providers;

    @Mock
    private ProviderInfoService providerInfoService;

    @BeforeEach
    public void beforeEach() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProviderInfoController(providerInfoService)).build();
        providers = new HashMap<>();
        providers.put(TEST_IMPL_OPENBANKING_NAME, new ProviderInfo(TEST_IMPL_OPENBANKING_DISPLAY_NAME, Collections.singletonMap(ServiceType.AIS, new ServiceInfo(null, null, null, null))));
        providers.put("FAKE_TEST_PROVIDER", new ProviderInfo("Fake Test Provider", Collections.singletonMap(ServiceType.AIS, new ServiceInfo(null, null, null, null))));
    }

    @Test
    public void shouldReturnAllProvidersWhenSendingGetRequestToProviderInfoEndpoint() throws Exception {
        // given
        Mockito.when(providerInfoService.getProvidersInfo()).thenReturn(providers);
        String providerInfoUrl = "/provider-info";

        // when
        MockHttpServletResponse response = mockMvc.perform(get(providerInfoUrl))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        Map<String, ProviderInfo> result = objectMapper.readValue(response.getContentAsString(), new TypeReference<>() {});
        assertThat(result).isEqualTo(providers);
        // We should not do any fancy checks on the data of the provider info, since these are coming from this project's submodules, such as 'open-banking'.
        // Whenever we would change something there it could break this test. Rather we should test specific provider-info data in the unit tests in 'ProviderInfoServiceTest'.
    }

    @Test
    public void shouldReturnProperProviderInfoWhenSendingGetRequestToProviderInfoProviderKeyEndpoint() throws Exception {
        // given
        String expectedProviderKey = "FAKE_TEST_PROVIDER";
        ProviderInfo expectedProviderInfo = providers.get(expectedProviderKey);
        Mockito.when(providerInfoService.getProviderInfo(expectedProviderKey)).thenReturn(Optional.of(expectedProviderInfo));
        String providerInfoUrl = "/provider-info/" + expectedProviderKey;

        // when
        MockHttpServletResponse response = mockMvc.perform(get(providerInfoUrl))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        ProviderInfo result = objectMapper.readValue(response.getContentAsString(), ProviderInfo.class);
        assertThat(result).isEqualTo(expectedProviderInfo);
    }

    @Test
    public void shouldReturnNotFoundResponseWhenSendingGetRequestToProviderInfoProviderKeyEndpointWithNonExistingProviderKey() throws Exception {
        // given
        String nonExistingProviderKey = "POSTBANK";
        String providerInfoUrl = "/provider-info/" + nonExistingProviderKey;

        // when
        MockHttpServletResponse response = mockMvc.perform(get(providerInfoUrl))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
}
