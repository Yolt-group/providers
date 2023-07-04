package com.yolt.providers.web.authenticationmeans;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.web.authenticationmeans.startuplogging.AuthenticationMeansLoggingProperties;
import com.yolt.providers.web.exception.ProviderNotFoundException;
import com.yolt.providers.web.service.ProviderFactoryService;
import lombok.SneakyThrows;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

import static com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans.CLIENT_SIGNING_CERTIFICATES_CHAIN_PEM;
import static com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans.CLIENT_TRANSPORT_CERTIFICATE_PEM;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientAuthenticationMeansCertificateVerifierServiceTest {

    private static final AuthenticationMeansReference AUTHENTICATION_MEANS_REFERENCE_CLIENT_ID = new AuthenticationMeansReference(UUID.randomUUID(), UUID.randomUUID());
    private static final AuthenticationMeansReference AUTHENTICATION_MEANS_REFERENCE_CLIENT_GROUPID = new AuthenticationMeansReference(null, UUID.randomUUID(), UUID.randomUUID());
    private static final AuthenticationMeansReference AUTHENTICATION_MEANS_REFERENCE_SCRAPER = new AuthenticationMeansReference(UUID.randomUUID(), null);

    @Mock
    private ProviderFactoryService providerFactoryService;
    @Mock
    private Provider provider;
    @Mock
    private Appender<ILoggingEvent> mockAppender;
    @Mock
    private AuthenticationMeansLoggingProperties loggingProperties;

    private ArgumentCaptor<ILoggingEvent> captorLoggingEvent;

    private ClientAuthenticationMeansCertificateVerifierService subject;

    @BeforeEach
    void beforeEach() {
        captorLoggingEvent = ArgumentCaptor.forClass(ILoggingEvent.class);
        Logger logger = (Logger) LoggerFactory.getLogger(ClientAuthenticationMeansCertificateVerifierService.class);
        logger.addAppender(mockAppender);
    }

    @AfterEach
    void teardown() {
        Logger logger = (Logger) LoggerFactory.getLogger(ClientAuthenticationMeansCertificateVerifierService.class);
        logger.detachAppender(mockAppender);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("authenticationMeansReferenceStreamData")
    void testCheckExpirationOfCertificate_certificateIsValidForLongerPeriodOfTimeAndWeDoNotGetWarn(AuthenticationMeansReference authenticationMeansReference) {
        //Given
        subject = new ClientAuthenticationMeansCertificateVerifierService(Clock.fixed(Instant.parse("2018-05-01T14:15:19Z"), ZoneId.systemDefault()), providerFactoryService, loggingProperties);
        mockProviderFactoryWithProviderUsingFollowingAuthenticationMeans("certificate", CLIENT_TRANSPORT_CERTIFICATE_PEM);
        Map<String, BasicAuthenticationMean> authenticationMeanMap = new HashMap<>();
        authenticationMeanMap.put("certificate", new BasicAuthenticationMean(CLIENT_TRANSPORT_CERTIFICATE_PEM.getType(), loadPemFile("certificates/fake/fake-certificate.pem")));

        //When
        subject.checkExpirationOfCertificate("ANY", ServiceType.AIS, authenticationMeansReference, authenticationMeanMap);

        //Then
        verify(mockAppender, times(1)).doAppend(captorLoggingEvent.capture());
        List<ILoggingEvent> values = captorLoggingEvent.getAllValues();
        ILoggingEvent iLoggingEvent = values.get(0);
        assertThat(iLoggingEvent.getMessage()).contains("Detected incoming expiration date for authentication means");
        assertThat(iLoggingEvent.getLevel()).isEqualTo(Level.INFO);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("authenticationMeansReferenceStreamData")
    void testcheckExpirationOfCertificate_certificateAboutToExpireIn2WeeksTimeAndWeGetTheLogOnce(AuthenticationMeansReference authenticationMeansReference) {
        //Given
        subject = new ClientAuthenticationMeansCertificateVerifierService(Clock.systemUTC(), providerFactoryService, loggingProperties);
        Map<String, BasicAuthenticationMean> authenticationMeanMap = new HashMap<>();
        mockProviderFactoryWithProviderUsingFollowingAuthenticationMeans("certificate", CLIENT_TRANSPORT_CERTIFICATE_PEM);
        authenticationMeanMap.put("certificate", new BasicAuthenticationMean(CLIENT_TRANSPORT_CERTIFICATE_PEM.getType(), loadPemFile("certificates/fake/fake-certificate.pem")));

        //When
        subject.checkExpirationOfCertificate("ANY", ServiceType.AIS, authenticationMeansReference, authenticationMeanMap);
        subject.checkExpirationOfCertificate("ANY", ServiceType.AIS, authenticationMeansReference, authenticationMeanMap);

        //Then
        verify(mockAppender, times(1)).doAppend(captorLoggingEvent.capture());
        List<ILoggingEvent> values = captorLoggingEvent.getAllValues();
        ILoggingEvent iLoggingEvent = values.get(0);
        assertThat(iLoggingEvent.getMessage()).contains("Detected incoming expiration date for authentication means");
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("authenticationMeansReferenceStreamData")
    void testcheckExpirationOfCertificate_certificateIsNotReadable(AuthenticationMeansReference authenticationMeansReference) {
        //Given
        subject = new ClientAuthenticationMeansCertificateVerifierService(Clock.systemUTC(), providerFactoryService, loggingProperties);
        mockProviderFactoryWithProviderUsingFollowingAuthenticationMeans("certificate", CLIENT_TRANSPORT_CERTIFICATE_PEM);
        Map<String, BasicAuthenticationMean> authenticationMeanMap = new HashMap<>();
        authenticationMeanMap.put("certificate", new BasicAuthenticationMean(CLIENT_TRANSPORT_CERTIFICATE_PEM.getType(), "THIS IS DEFINITELY NOT A CERTIFICATE"));

        //When
        subject.checkExpirationOfCertificate("ANY", ServiceType.AIS, authenticationMeansReference, authenticationMeanMap);
        subject.checkExpirationOfCertificate("ANY", ServiceType.AIS, authenticationMeansReference, authenticationMeanMap);

        //Then
        verify(mockAppender, times(1)).doAppend(captorLoggingEvent.capture());
        List<ILoggingEvent> values = captorLoggingEvent.getAllValues();
        ILoggingEvent iLoggingEvent = values.get(0);
        assertThat(iLoggingEvent.getMessage()).contains("error converting certificate ", "certificate");
        assertThat(iLoggingEvent.getMessage()).doesNotContain("THIS IS DEFINITELY NOT A CERTIFICATE");
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("authenticationMeansReferenceStreamData")
    void testcheckExpirationOfCertificate_certificateChainIsVerified(AuthenticationMeansReference authenticationMeansReference) {
        //Given
        subject = new ClientAuthenticationMeansCertificateVerifierService(Clock.fixed(Instant.parse("3006-02-01T14:15:19Z"), ZoneId.systemDefault()), providerFactoryService, loggingProperties);
        mockProviderFactoryWithProviderUsingFollowingAuthenticationMeans("certificate_chain", CLIENT_SIGNING_CERTIFICATES_CHAIN_PEM);
        Map<String, BasicAuthenticationMean> authenticationMeanMap = new HashMap<>();
        authenticationMeanMap.put("certificate_chain", new BasicAuthenticationMean(CLIENT_SIGNING_CERTIFICATES_CHAIN_PEM.getType(), loadPemFile("certificates/fake/fake-certificate-chain.pem")));

        //When
        subject.checkExpirationOfCertificate("ANY", ServiceType.AIS, authenticationMeansReference, authenticationMeanMap);
        subject.checkExpirationOfCertificate("ANY", ServiceType.AIS, authenticationMeansReference, authenticationMeanMap);

        //Then
        verify(mockAppender, times(2)).doAppend(captorLoggingEvent.capture());
        List<ILoggingEvent> values = captorLoggingEvent.getAllValues();
        ILoggingEvent firstLoggingEvent = values.get(0);
        assertThat(firstLoggingEvent.getMessage()).contains("Detected incoming expiration date for authentication means");
        assertThat(firstLoggingEvent.getLevel()).isEqualTo(Level.WARN);
        ILoggingEvent secondLoggingEvent = values.get(1);
        assertThat(secondLoggingEvent.getMessage()).contains("Detected incoming expiration date for authentication means");
        assertThat(firstLoggingEvent.getLevel()).isEqualTo(Level.WARN);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("authenticationMeansReferenceStreamData")
    void testcheckExpirationOfCertificate_certificateIsNotUsedAsAnAuthenticationMeanInImplementationAndIsNotLogged(AuthenticationMeansReference authenticationMeansReference) {
        //Given
        subject = new ClientAuthenticationMeansCertificateVerifierService(Clock.systemUTC(), providerFactoryService, loggingProperties);
        mockProviderFactoryWithProviderUsingFollowingAuthenticationMeans("certificate", CLIENT_TRANSPORT_CERTIFICATE_PEM);
        Map<String, BasicAuthenticationMean> authenticationMeanMap = singletonMap("certificate_old", new BasicAuthenticationMean(CLIENT_TRANSPORT_CERTIFICATE_PEM.getType(), loadPemFile("certificates/fake/fake-certificate.pem")));

        //When
        subject.checkExpirationOfCertificate("ANY", ServiceType.AIS, authenticationMeansReference, authenticationMeanMap);

        //Then
        verify(mockAppender, never()).doAppend(captorLoggingEvent.capture());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("authenticationMeansReferenceStreamData")
    void testcheckExpirationOfCertificate_providerIsNonExistingAndTheWarnAppearsJustOneTime(AuthenticationMeansReference authenticationMeansReference) {
        //Given
        subject = new ClientAuthenticationMeansCertificateVerifierService(Clock.systemUTC(), providerFactoryService, loggingProperties);
        doThrow(ProviderNotFoundException.class).when(providerFactoryService).getProvider(eq("NOT_FOUND"), any(), eq(ServiceType.AIS), any());
        Map<String, BasicAuthenticationMean> authenticationMeanMap = singletonMap("certificate", new BasicAuthenticationMean(CLIENT_TRANSPORT_CERTIFICATE_PEM.getType(), loadPemFile("certificates/fake/fake-certificate.pem")));

        //When
        subject.checkExpirationOfCertificate("NOT_FOUND", ServiceType.AIS, authenticationMeansReference, authenticationMeanMap);

        //Then
        verify(mockAppender, atLeast(1)).doAppend(captorLoggingEvent.capture());
        List<ILoggingEvent> values = captorLoggingEvent.getAllValues();
        ILoggingEvent iLoggingEvent = values.get(0);
        assertThat(iLoggingEvent.getMessage()).contains("Cannot fetch provider");
    }

    private void mockProviderFactoryWithProviderUsingFollowingAuthenticationMeans(String authMeanName, TypedAuthenticationMeans typedAuthenticationMeans) {
        when(providerFactoryService.getProvider(eq("ANY"), any(), eq(ServiceType.AIS), any())).thenReturn(provider);
        when(provider.getTypedAuthenticationMeans()).thenReturn(singletonMap(authMeanName, typedAuthenticationMeans));
    }

    private static Stream<Arguments> authenticationMeansReferenceStreamData() {
        return Stream.of(
                Arguments.of(AUTHENTICATION_MEANS_REFERENCE_CLIENT_ID),
                Arguments.of(AUTHENTICATION_MEANS_REFERENCE_CLIENT_GROUPID),
                Arguments.of(AUTHENTICATION_MEANS_REFERENCE_SCRAPER));
    }

    private static String loadPemFile(final String filename) throws IOException, URISyntaxException {
        URI uri = Objects.requireNonNull(ClientAuthenticationMeansCertificateVerifierService.class
                .getClassLoader()
                .getResource(filename))
                .toURI();
        Path filePath = new File(uri).toPath();
        return String.join("\n", Files.readAllLines(filePath, StandardCharsets.UTF_8));
    }
}