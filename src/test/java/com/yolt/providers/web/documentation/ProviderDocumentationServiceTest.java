package com.yolt.providers.web.documentation;

import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.dummy.DummyTestProvider;
import com.yolt.providers.dummy.FakeTestDataProvider;
import com.yolt.providers.dummy.MockTestProvider;
import com.yolt.providers.web.service.ProviderFactoryService;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProviderDocumentationServiceTest {

    @Mock
    private ProviderFactoryService providerFactoryService;

    private ProviderDocumentationService subject;

    @BeforeEach
    public void beforeEach() {
        subject = new ProviderDocumentationService(providerFactoryService);
    }

    @Test
    public void shouldReturnNotPresentResultWhenArgumentsAreNull() {
        // given
        String providerIdentifier = null;
        ServiceType serviceType = null;

        // when
        Optional<ProviderDocumentation> result = subject.getProviderDocumentation(providerIdentifier, serviceType);

        // then
        assertThat(result).isNotPresent();
    }

    @Test
    public void shouldReturnNotPresentResultWhenDocumentationIsNotExisting() {
        // given
        String providerIdentifier = "ProviderWithoutADocumentation";
        ServiceType serviceType = ServiceType.AIS;

        // when
        Optional<ProviderDocumentation> result = subject.getProviderDocumentation(providerIdentifier, serviceType);

        // then
        assertThat(result).isNotPresent();
    }

    @Test
    public void shouldReturnValidResultWhenDocumentationIsExisting() {
        // given
        String providerIdentifier = "TEST_IMPL_OPENBANKING_MOCK";
        ServiceType serviceType = ServiceType.AIS;

        // when
        Optional<ProviderDocumentation> result = subject.getProviderDocumentation(providerIdentifier, serviceType);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(new ProviderDocumentation(providerIdentifier, serviceType, "IyMgVEVTVF9JTVBMX09QRU5CQU5LSU5HX01PQ0sgQUlTX1Byb3ZpZGVyCg=="));
    }

    @Test
    public void shouldReturnValidResultWhenRequestingAllDocumentations() {
        // given
        List<Provider> providers = asList(new DummyTestProvider(), new FakeTestDataProvider(), new MockTestProvider());
        when(providerFactoryService.getAllStableProviders()).thenReturn(providers);

        // when
        List<ProviderDocumentation> result = subject.getProvidersDocumentation();

        // then
        assertThat(result).containsExactlyInAnyOrder(
                new ProviderDocumentation("TEST_IMPL_OPENBANKING_MOCK", ServiceType.AIS, "IyMgVEVTVF9JTVBMX09QRU5CQU5LSU5HX01PQ0sgQUlTX1Byb3ZpZGVyCg=="),
                new ProviderDocumentation("FAKE_TEST_PROVIDER", ServiceType.AIS, "IyMgRkFLRV9URVNUX1BST1ZJREVSIEFJU19Qcm92aWRlcgo="));
    }
}
