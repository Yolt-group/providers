package com.yolt.providers.web.cryptography.trust;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class EmitSEMaEventOnCertificateRotationHostnameVerifierTest {

    protected Appender<ILoggingEvent> mockAppender;
    protected ArgumentCaptor<ILoggingEvent> captorLoggingEvent;

    @BeforeEach
    public void beforeEach() {
        mockAppender = mock(Appender.class);
        captorLoggingEvent = ArgumentCaptor.forClass(ILoggingEvent.class);
        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.addAppender(mockAppender);
    }

    @AfterEach
    public void teardown() {
        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.detachAppender(mockAppender);
    }

    /**
     * Check that the {@link HostnameVerifier} delegate is used by {@link EmitSEMaEventOnCertificateRotationHostnameVerifier}, no SEMa event.
     */
    @Test
    public void shouldReturnFalseForVerifyWhenDelegateCannotVerifyHostname() {
        // given
        final HostnameVerifier delegate = Mockito.mock(HostnameVerifier.class);
        when(delegate.verify(any(), any())).thenReturn(false);
        final EmitSEMaEventOnCertificateRotationHostnameVerifier subject = new EmitSEMaEventOnCertificateRotationHostnameVerifier(delegate);

        // when
        final boolean trusted = subject.verify(null, null);

        // then
        assertThat(trusted).isFalse();
        verify(delegate).verify(any(), any());
        assertNoSEMaEventLogged();
    }

    /**
     * First use: trusted, no SEMa event.
     */
    @Test
    @SneakyThrows
    public void shouldReturnTrueForVerifyWhenNoPreviousConnections() {
        // given
        final HostnameVerifier delegate = Mockito.mock(HostnameVerifier.class);
        when(delegate.verify(any(), any())).thenReturn(true);
        final EmitSEMaEventOnCertificateRotationHostnameVerifier subject = new EmitSEMaEventOnCertificateRotationHostnameVerifier(delegate);
        final X509Certificate cert = Mockito.mock(X509Certificate.class);
        final SSLSession sslSession = Mockito.mock(SSLSession.class);
        when(sslSession.getPeerCertificates()).thenReturn(new Certificate[]{cert});

        // when
        final boolean trusted = subject.verify("example.com", sslSession);

        // then
        assertThat(trusted).isTrue();
        assertNoSEMaEventLogged();
    }

    /**
     * Subsequent use: trusted, no SEMa event.
     */
    @Test
    @SneakyThrows
    public void shouldReturnTrueForFirstAndSubsequentCallForVerifyWhenPreviousConnectionWithTheSameCertAsSubsequent() {
        // given
        final HostnameVerifier delegate = Mockito.mock(HostnameVerifier.class);
        when(delegate.verify(any(), any())).thenReturn(true);
        final EmitSEMaEventOnCertificateRotationHostnameVerifier subject = new EmitSEMaEventOnCertificateRotationHostnameVerifier(delegate);
        final X509Certificate cert = Mockito.mock(X509Certificate.class);
        final SSLSession sslSession = Mockito.mock(SSLSession.class);
        when(sslSession.getPeerCertificates()).thenReturn(new Certificate[]{cert});

        // when
        final boolean result1 = subject.verify("example.com", sslSession);
        final boolean result2 = subject.verify("example.com", sslSession);

        // then
        assertThat(result1).isTrue();
        assertThat(result2).isTrue();
        assertNoSEMaEventLogged();
    }

    /**
     * New certificate: trusted, SEMa event.
     */
    @Test
    @SneakyThrows
    public void shouldReturnTrueForFirstAndSubsequentCallsForVerifyWhenSubsequentConnectionWithDifferentCertificateThenFirstOne() {
        // given
        final HostnameVerifier delegate = Mockito.mock(HostnameVerifier.class);
        when(delegate.verify(any(), any())).thenReturn(true);
        final EmitSEMaEventOnCertificateRotationHostnameVerifier subject = new EmitSEMaEventOnCertificateRotationHostnameVerifier(delegate);
        final X509Certificate certA = Mockito.mock(X509Certificate.class);
        final SSLSession sslSessionWithCertA = Mockito.mock(SSLSession.class);
        when(sslSessionWithCertA.getPeerCertificates()).thenReturn(new Certificate[]{certA});
        final X509Certificate certB = Mockito.mock(X509Certificate.class);
        final SSLSession sslSessionWithCertB = Mockito.mock(SSLSession.class);
        when(sslSessionWithCertB.getPeerCertificates()).thenReturn(new Certificate[]{certB});

        // when
        final boolean result1 = subject.verify("example.com", sslSessionWithCertA);
        final boolean result2 = subject.verify("example.com", sslSessionWithCertB);
        assertThat(result1).isTrue();
        assertThat(result2).isTrue();

        final boolean result3 = subject.verify("example.com", sslSessionWithCertA);
        // then

        assertThat(result3).isTrue();
        assertSEMaEventLogged();
    }

    /**
     * One of the certificates is reused.
     */
    @Test
    @SneakyThrows
    public void shouldReturnTrueForEachAndNotTriggerSemaEventIfTheBankIsReusingOneOfTheOlderCertificates() {
        // given
        final HostnameVerifier delegate = Mockito.mock(HostnameVerifier.class);
        when(delegate.verify(any(), any())).thenReturn(true);
        final EmitSEMaEventOnCertificateRotationHostnameVerifier subject = new EmitSEMaEventOnCertificateRotationHostnameVerifier(delegate);
        final X509Certificate certA = Mockito.mock(X509Certificate.class);
        final SSLSession sslSessionWithCertA = Mockito.mock(SSLSession.class);
        when(sslSessionWithCertA.getPeerCertificates()).thenReturn(new Certificate[]{certA});
        final X509Certificate certB = Mockito.mock(X509Certificate.class);
        final SSLSession sslSessionWithCertB = Mockito.mock(SSLSession.class);
        when(sslSessionWithCertB.getPeerCertificates()).thenReturn(new Certificate[]{certB});

        // when
        final boolean result1 = subject.verify("example.com", sslSessionWithCertA);
        final boolean result2 = subject.verify("example.com", sslSessionWithCertB);
        final boolean result3 = subject.verify("example.com", sslSessionWithCertA);

        // then
        assertThat(result1).isTrue();
        assertThat(result2).isTrue();
        assertThat(result3).isTrue();
        assertSEMaEventLogged();
    }

    private void assertNoSEMaEventLogged() {
        verify(mockAppender, times(0)).doAppend(any());
    }

    private void assertSEMaEventLogged() {
        verify(mockAppender, times(1)).doAppend(any());
        verify(mockAppender).doAppend(captorLoggingEvent.capture());
        final ILoggingEvent loggingEvent = captorLoggingEvent.getValue();
        assertThat(loggingEvent.getMarker().toString()).isEqualTo("log_type=SEMA, sema_type=com.yolt.providers.web.cryptography.trust.RotatedPeerCertificateSEMaEvent, {new-cert=<failed to encode>, hostname=example.com, old-cert=<failed to encode>}");
    }
}