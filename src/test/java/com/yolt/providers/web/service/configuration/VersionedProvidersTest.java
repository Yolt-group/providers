package com.yolt.providers.web.service.configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.common.versioning.ProviderVersion;
import com.yolt.providers.web.exception.ProviderDuplicateException;
import com.yolt.providers.web.exception.ProviderNotFoundException;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.yolt.providers.common.versioning.ProviderVersion.*;
import static com.yolt.providers.web.service.configuration.VersionType.EXPERIMENTAL;
import static com.yolt.providers.web.service.configuration.VersionType.STABLE;
import static nl.ing.lovebird.providerdomain.ServiceType.AIS;
import static nl.ing.lovebird.providerdomain.ServiceType.PIS;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class VersionedProvidersTest {

    private Map<String, VersionProperties.ProviderVersions> propertyProviders;
    private VersionProperties properties;
    private List<Provider> implemented;

    @BeforeEach
    public void beforeEach() {
        propertyProviders = new HashMap<>();
        properties = new VersionProperties(propertyProviders);
        implemented = new ArrayList<>();
    }

    @Test
    public void shouldInitializeWithSuccessForNewVersionedProvidersWithCorrectData() {
        // given
        propertyProvider("PROVIDER1")
                .withAis(VERSION_1, VERSION_2)
                .withPis(VERSION_1, VERSION_1)
                .build();
        propertyProvider("PROVIDER2")
                .withAis(VERSION_2, VERSION_2)
                .build();
        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_1)
                .build();
        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_2)
                .build();
        provider("PROVIDER1")
                .withServiceType(PIS)
                .withVersion(VERSION_1)
                .build();
        provider("PROVIDER2")
                .withServiceType(AIS)
                .withVersion(VERSION_2)
                .build();

        // when
        ThrowableAssert.ThrowingCallable newVersionedProvidersCallable = () -> new VersionedProviders(properties, implemented);

        // then
        assertThatCode(newVersionedProvidersCallable)
                .doesNotThrowAnyException();
    }

    @Test
    public void shouldInitializeWithSuccessForNewVersionedProvidersWhenImplementationNotInProperties() {
        // given
        propertyProvider("PROVIDER1")
                .withAis(VERSION_1, VERSION_1)
                .build();

        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_1)
                .build();
        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_2)
                .build();
        // when
        ThrowableAssert.ThrowingCallable newVersionedProvidersCallable = () -> new VersionedProviders(properties, implemented);

        // then
        assertThatCode(newVersionedProvidersCallable)
                .doesNotThrowAnyException();
    }

    @Test
    public void shouldAppendProperWarnLogForNewVersionedProvidersWithUnusedProviders() {
        // given
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        final Appender mockAppender = mock(Appender.class);
        root.addAppender(mockAppender);
        propertyProvider("PROVIDER1")
                .withAis(VERSION_1, VERSION_1)
                .build();
        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_1)
                .build();
        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_2)
                .build();

        // when
        new VersionedProviders(properties, implemented);

        // then
        verify(mockAppender).doAppend(argThat((ArgumentMatcher) argument -> {
            LoggingEvent event = (LoggingEvent) argument;
            return Level.WARN.equals(event.getLevel()) && event.getFormattedMessage().contains("There are not used providers: VersionedProviders.ProviderIdKey(providerKey=PROVIDER1, serviceType=AIS, version=VERSION_2)");
        }));
    }

    @Test
    public void shouldThrowProviderNotFoundExceptionForNewVersionedProvidersWhenMissingImplementation() {
        // given
        propertyProvider("PROVIDER1")
                .withAis(VERSION_1, VERSION_2)
                .build();
        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_1)
                .build();

        // when
        ThrowableAssert.ThrowingCallable newVersionedProvidersCallable = () -> new VersionedProviders(properties, implemented);

        // then
        assertThatThrownBy(newVersionedProvidersCallable)
                .isInstanceOf(ProviderNotFoundException.class);
    }

    @Test
    public void shouldThrowIllegalStateExceptionForNewVersionedProvidersWithProviderWithEmptyProviderKey() {
        // given
        provider("")
                .withServiceType(AIS)
                .withVersion(VERSION_1)
                .build();
        // when
        ThrowableAssert.ThrowingCallable newVersionedProvidersCallable = () -> new VersionedProviders(properties, implemented);

        // then
        assertThatThrownBy(newVersionedProvidersCallable)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void shouldThrowIllegalStateExceptionForNewVersionedProvidersWithProviderWithNullProviderKey() {
        // given
        provider(null)
                .withServiceType(AIS)
                .withVersion(VERSION_1)
                .build();

        // when
        ThrowableAssert.ThrowingCallable newVersionedProvidersCallable = () -> new VersionedProviders(properties, implemented);

        // then
        assertThatThrownBy(newVersionedProvidersCallable)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void shouldThrowProviderDuplicateExceptionForNewVersionedProvidersWithDuplicatedProvider() {
        // given
        propertyProvider("PROVIDER1")
                .withAis(VERSION_1, VERSION_2)
                .build();
        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_1)
                .build();
        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_1)
                .build();

        // when
        ThrowableAssert.ThrowingCallable newVersionedProvidersCallable = () -> new VersionedProviders(properties, implemented);

        // then
        assertThatThrownBy(newVersionedProvidersCallable)
                .isInstanceOf(ProviderDuplicateException.class);
    }

    @Test
    public void shouldInitializeWithSuccessForNewVersionedProvidersWhenNullInProperties() {
        // given
        VersionProperties versionProperties = new VersionProperties();

        // when
        ThrowableAssert.ThrowingCallable newVersionedProvidersCallable = () -> new VersionedProviders(versionProperties, implemented);

        // then
        assertThatCode(newVersionedProvidersCallable)
                .doesNotThrowAnyException();
    }

    @Test
    public void shouldReturnProperProviderVersionImplementationForGetProviderWithCorrectData() {
        // given
        propertyProvider("PROVIDER1")
                .withAis(VERSION_1, VERSION_2)
                .withPis(VERSION_2, VERSION_3)
                .build();
        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_1)
                .build();
        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_2)
                .build();
        provider("PROVIDER1")
                .withServiceType(PIS)
                .withVersion(VERSION_2)
                .build();
        provider("PROVIDER1")
                .withServiceType(PIS)
                .withVersion(VERSION_3)
                .build();
        VersionedProviders versionedProviders = new VersionedProviders(properties, implemented);

        // when
        Provider stableAisProviderVersion = versionedProviders.getProvider("PROVIDER1", AIS, STABLE);
        Provider experimentalAisProviderVersion = versionedProviders.getProvider("PROVIDER1", AIS, EXPERIMENTAL);
        Provider stablePisProviderVersion = versionedProviders.getProvider("PROVIDER1", PIS, STABLE);
        Provider experimentalPisProviderVersion = versionedProviders.getProvider("PROVIDER1", PIS, EXPERIMENTAL);

        //then
        assertThat(stableAisProviderVersion.getProviderIdentifier()).isEqualTo("PROVIDER1");
        assertThat(stableAisProviderVersion.getServiceType()).isEqualTo(AIS);
        assertThat(stableAisProviderVersion.getVersion()).isEqualTo(VERSION_1);
        assertThat(experimentalAisProviderVersion.getProviderIdentifier()).isEqualTo("PROVIDER1");
        assertThat(experimentalAisProviderVersion.getServiceType()).isEqualTo(AIS);
        assertThat(experimentalAisProviderVersion.getVersion()).isEqualTo(VERSION_2);

        assertThat(stablePisProviderVersion.getProviderIdentifier()).isEqualTo("PROVIDER1");
        assertThat(stablePisProviderVersion.getServiceType()).isEqualTo(PIS);
        assertThat(stablePisProviderVersion.getVersion()).isEqualTo(VERSION_2);
        assertThat(experimentalPisProviderVersion.getProviderIdentifier()).isEqualTo("PROVIDER1");
        assertThat(experimentalPisProviderVersion.getServiceType()).isEqualTo(PIS);
        assertThat(experimentalPisProviderVersion.getVersion()).isEqualTo(VERSION_3);
    }

    @Test
    public void shouldReturnNullForGetProviderWithServiceTypeThatIsNotAvailableForThisProvider() {
        //given
        propertyProvider("PROVIDER1")
                .withAis(VERSION_1, VERSION_2)
                .build();

        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_1)
                .build();
        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_2)
                .build();
        VersionedProviders versionedProviders = new VersionedProviders(properties, implemented);

        //when
        Provider provider = versionedProviders.getProvider("PROVIDER1", PIS, STABLE);

        //then
        assertThat(provider).isNull();
    }

    @Test
    public void shouldThrowProviderNotFoundExceptionForGetProviderWhenProviderIsMissing() {
        // given
        VersionedProviders versionedProviders = new VersionedProviders(properties, implemented);

        // when
        ThrowableAssert.ThrowingCallable getProviderCallable = () -> versionedProviders.getProvider("PROVIDER", AIS, STABLE);

        // then
        assertThatThrownBy(getProviderCallable)
                .isInstanceOf(ProviderNotFoundException.class);
    }

    @Test
    public void shouldThrowProviderNotFoundExceptionForGetProviderWhenNullInProperties() {
        // given
        VersionedProviders versionedProviders = new VersionedProviders(new VersionProperties(), implemented);

        // when
        ThrowableAssert.ThrowingCallable getProviderCallable = () -> versionedProviders.getProvider("PROVIDER", AIS, STABLE);

        // then
        assertThatThrownBy(getProviderCallable)
                .isInstanceOf(ProviderNotFoundException.class);
    }

    @Test
    public void shouldThrowUnsupportedOperationExceptionForGetProviderWithUnsupportedServiceType() {
        // given
        propertyProvider("PROVIDER1")
                .withAis(VERSION_1, VERSION_1)
                .build();

        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_1)
                .build();
        VersionedProviders versionedProviders = new VersionedProviders(properties, implemented);

        // when
        ThrowableAssert.ThrowingCallable getProviderCallable = () -> versionedProviders.getProvider("PROVIDER1", ServiceType.IC, STABLE);

        // then
        assertThatThrownBy(getProviderCallable)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void shouldReturnAllStableProvidersImplementationsForGetAllStableProviders() {
        // given
        propertyProvider("PROVIDER1")
                .withAis(VERSION_1, VERSION_2)
                .withPis(VERSION_1, VERSION_1)
                .build();
        propertyProvider("PROVIDER2")
                .withAis(VERSION_2, VERSION_2)
                .build();
        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_1)
                .build();
        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_2)
                .build();
        provider("PROVIDER1")
                .withServiceType(PIS)
                .withVersion(VERSION_1)
                .build();
        provider("PROVIDER2")
                .withServiceType(AIS)
                .withVersion(VERSION_2)
                .build();

        VersionedProviders versionedProviders = new VersionedProviders(properties, implemented);

        // when
        List<Provider> stableProviders = versionedProviders.getAllStableProviders();

        // then
        assertThat(stableProviders).hasSize(3);
        assertThat(stableProviders)
                .extracting(Provider::getProviderIdentifier, Provider::getServiceType, Provider::getVersion)
                .contains(tuple("PROVIDER1", AIS, VERSION_1),
                        tuple("PROVIDER1", PIS, VERSION_1),
                        tuple("PROVIDER2", AIS, VERSION_2));
    }

    @Test
    public void shouldFilterOutProviderThatIsNotListedInPropertiesForGetAllStableProviders() {
        // given
        propertyProvider("PROVIDER1")
                .withAis(VERSION_1, VERSION_1)
                .build();
        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_1)
                .build();
        provider("PROVIDER1")
                .withServiceType(PIS)
                .withVersion(VERSION_1)
                .build();

        VersionedProviders versionedProviders = new VersionedProviders(properties, implemented);

        // when
        List<Provider> stableProviders = versionedProviders.getAllStableProviders();

        // then
        assertThat(stableProviders).hasSize(1);
        assertThat(stableProviders)
                .extracting(Provider::getProviderIdentifier, Provider::getServiceType, Provider::getVersion)
                .contains(tuple("PROVIDER1", AIS, VERSION_1));
    }


    @Test
    public void shouldReturnAllStableProviderImplementationsForGetStableProvidersWithExistingProviderId() {
        // given
        propertyProvider("PROVIDER1")
                .withAis(VERSION_1, VERSION_2)
                .withPis(VERSION_1, VERSION_1)
                .build();
        propertyProvider("PROVIDER2")
                .withAis(VERSION_2, VERSION_2)
                .build();
        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_1)
                .build();
        provider("PROVIDER1")
                .withServiceType(AIS)
                .withVersion(VERSION_2)
                .build();
        provider("PROVIDER1")
                .withServiceType(PIS)
                .withVersion(VERSION_1)
                .build();
        provider("PROVIDER2")
                .withServiceType(AIS)
                .withVersion(VERSION_2)
                .build();
        VersionedProviders versionedProviders = new VersionedProviders(properties, implemented);

        // when
        List<Provider> stableProviders = versionedProviders.getStableProviders("PROVIDER1");

        // then
        assertThat(stableProviders).hasSize(2);
        assertThat(stableProviders)
                .extracting(Provider::getProviderIdentifier, Provider::getServiceType, Provider::getVersion)
                .contains(tuple("PROVIDER1", AIS, VERSION_1),
                        tuple("PROVIDER1", PIS, VERSION_1));
    }

    @Test
    public void shouldReturnEmptyListForGetStableProvidersWithNonExistingProviderId() {
        // given
        VersionedProviders versionedProviders = new VersionedProviders(properties, implemented);

        // when
        List<Provider> stableProviders = versionedProviders.getStableProviders("PROVIDER1");

        // then
        assertThat(stableProviders).isEmpty();
    }

    private ProviderBuilder provider(final String providerKey) {
        return new ProviderBuilder(providerKey);
    }

    private PropertyProviderBuilder propertyProvider(final String providerKey) {
        return new PropertyProviderBuilder(providerKey);
    }

    private class ProviderBuilder<T extends Provider> {

        private String providerKey;
        private ServiceType serviceType;
        private ProviderVersion version;

        ProviderBuilder(final String providerKey) {
            this.providerKey = providerKey;
        }

        private ProviderBuilder withServiceType(final ServiceType serviceType) {
            this.serviceType = serviceType;
            return this;
        }

        private ProviderBuilder withVersion(final ProviderVersion version) {
            this.version = version;
            return this;
        }

        private void build() {
            Provider provider = mock(Provider.class);
            when(provider.getVersion()).thenReturn(version);
            when(provider.getProviderIdentifier()).thenReturn(providerKey);
            when(provider.getServiceType()).thenReturn(serviceType);
            implemented.add(provider);
        }

        private Provider get() {
            Provider provider = mock(Provider.class);
            when(provider.getVersion()).thenReturn(version);
            when(provider.getProviderIdentifier()).thenReturn(providerKey);
            when(provider.getServiceType()).thenReturn(serviceType);
            return provider;
        }
    }

    private class PropertyProviderBuilder {

        PropertyProviderBuilder(final String providerKey) {
            this.providerKey = providerKey;
        }

        private String providerKey;
        private ProviderVersion aisStable;
        private ProviderVersion aisExperimental;
        private ProviderVersion pisStable;
        private ProviderVersion pisExperimental;

        private PropertyProviderBuilder withAis(final ProviderVersion stableVersion, final ProviderVersion experimentalVersion) {
            aisStable = stableVersion;
            aisExperimental = experimentalVersion;
            return this;
        }

        private PropertyProviderBuilder withPis(final ProviderVersion stableVersion, final ProviderVersion experimentalVersion) {
            pisStable = stableVersion;
            pisExperimental = experimentalVersion;
            return this;
        }

        private void build() {
            VersionProperties.Version aisVersions = createVersion(aisStable, aisExperimental);
            VersionProperties.Version pisVersions = createVersion(pisStable, pisExperimental);
            propertyProviders.put(providerKey,
                    new VersionProperties.ProviderVersions(
                            aisVersions,
                            pisVersions
                    )
            );
        }

        private VersionProperties.Version createVersion(final ProviderVersion aisStable, final ProviderVersion aisExperimental) {
            if (aisStable != null && aisExperimental != null) {
                return new VersionProperties.Version(aisStable, aisExperimental);
            }
            return null;
        }
    }
}