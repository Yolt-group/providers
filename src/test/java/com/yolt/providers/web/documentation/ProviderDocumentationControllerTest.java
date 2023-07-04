package com.yolt.providers.web.documentation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.web.configuration.TestConfiguration;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ExtendWith(MockitoExtension.class)
public class ProviderDocumentationControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new TestConfiguration().jacksonObjectMapper();
    private ProviderDocumentation firstAISProvider;
    private List<ProviderDocumentation> providersDocumentations;

    @Mock
    private ProviderDocumentationService providerDocumentationService;

    @BeforeEach
    public void beforeEach() {
        ProviderDocumentationController subject = new ProviderDocumentationController(providerDocumentationService);
        mockMvc = MockMvcBuilders.standaloneSetup(subject).build();

        providersDocumentations = new ArrayList<>(3);
        firstAISProvider = new ProviderDocumentation("FirstProvider", ServiceType.AIS, "firstAISDocumentation");
        providersDocumentations.add(firstAISProvider);
        providersDocumentations.add(new ProviderDocumentation("FirstProvider", ServiceType.PIS, "firstPISDocumentation"));
        providersDocumentations.add(new ProviderDocumentation("SecondProvider", ServiceType.AIS, "secondAISDocumentation"));
    }

    @Test
    public void shouldReturnProperProviderDocumentationWhenSendingGetRequestToProviderDocumentationEndpoint() throws Exception {
        // given
        when(providerDocumentationService.getProvidersDocumentation()).thenReturn(providersDocumentations);
        String requestUrl = "/internal-documentation";

        // when
        MockHttpServletResponse response = mockMvc.perform(get(requestUrl))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        List<ProviderDocumentation> result = objectMapper.readValue(response.getContentAsString(), new TypeReference<>() {});
        assertThat(result).hasSize(3);
        assertThat(result).isEqualTo(providersDocumentations);
    }

    @Test
    public void shouldReturnProperProviderDocumentationWhenSendingGetRequestForParticularProviderToProviderDocumentationEndpoint() throws Exception {
        // given
        when(providerDocumentationService.getProviderDocumentation(eq("FirstProvider"), eq(ServiceType.AIS))).thenReturn(Optional.of(firstAISProvider));
        String requestUrl = "/internal-documentation/provider/FirstProvider/serviceType/AIS";

        // when
        MockHttpServletResponse response = mockMvc.perform(get(requestUrl))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        ProviderDocumentation result = objectMapper.readValue(response.getContentAsString(), new TypeReference<>() {});
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(firstAISProvider);
    }
}
