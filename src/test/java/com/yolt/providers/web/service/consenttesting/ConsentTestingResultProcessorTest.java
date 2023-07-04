package com.yolt.providers.web.service.consenttesting;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.web.service.domain.ConsentTestingMessage;
import com.yolt.providers.web.service.domain.ConsentTestingResult;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentTestingResultProcessorTest {

    private static final String CONSENT_TESTING_RESULT_METRIC_NAME = "consent_testing_result";
    private static final String PROVIDER_IDENTIFIER_METRIC_TAG = "provider_identifier";
    private static final String PROVIDER_SERVICETYPE_METRIC_TAG = "provider_servicetype";
    private static final String RESULT_METRIC_TAG = "result";
    private static final UUID MOCK_CLIENT_ID = UUID.randomUUID();
    private static final UUID MOCK_CLIENT_REDIRECT_ID = UUID.randomUUID();
    private static final String CLIENT_ID_METRIC_TAG = "client_id";

    @Mock
    private Appender<ILoggingEvent> mockedAppender;
    @Mock
    private Provider provider;

    private MeterRegistry meterRegistry;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventCaptor;

    @InjectMocks
    private ConsentTestingResultProcessor consentTestingResultProcessor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        consentTestingResultProcessor = new ConsentTestingResultProcessor(meterRegistry);
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(mockedAppender);
        root.setLevel(Level.INFO);
        when(provider.getProviderIdentifier())
                .thenReturn("SOME_PROVIDER");
        when(provider.getServiceType())
                .thenReturn(ServiceType.AIS);
    }

    @Test
    public void shouldLogWarnForNotGeneratedConsentPage() {
        //given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(MOCK_CLIENT_ID, MOCK_CLIENT_REDIRECT_ID);

        // when
        consentTestingResultProcessor.processConsentTestingResult(provider, new ConsentTestingResult(ConsentTestingMessage.NOT_GENERATED, null), authenticationMeansReference);

        // then
        verify(mockedAppender, times(3)).doAppend(loggingEventCaptor.capture());
        ILoggingEvent log = loggingEventCaptor.getAllValues().get(0);
        assertThat(log.getLevel()).isEqualTo(Level.WARN);
        assertThat(log.getFormattedMessage()).contains(ConsentTestingMessage.NOT_GENERATED.getMessage());
        assertThat(log.getMDCPropertyMap().get(ConsentTestingResultProcessor.CONSENT_TESTING_RESULT_METRIC_NAME)).isEqualTo(ConsentTestingMessage.NOT_GENERATED.name());

        Meter.Id consentTestingMetric = meterRegistry.getMeters().get(0).getId();
        assertThat(consentTestingMetric.getName()).isEqualTo(CONSENT_TESTING_RESULT_METRIC_NAME);
        assertThat(consentTestingMetric.getTag(PROVIDER_IDENTIFIER_METRIC_TAG)).isEqualTo("SOME_PROVIDER");
        assertThat(consentTestingMetric.getTag(PROVIDER_SERVICETYPE_METRIC_TAG)).isEqualTo("AIS");
        assertThat(consentTestingMetric.getTag(RESULT_METRIC_TAG)).isEqualTo("failure");
        assertThat(consentTestingMetric.getTag(CLIENT_ID_METRIC_TAG)).isEqualTo(MOCK_CLIENT_ID.toString());
    }

    @Test
    public void shouldLogWarnForOnlyGeneratedConsentPage() {
        //given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(MOCK_CLIENT_ID, MOCK_CLIENT_REDIRECT_ID);

        // when
        consentTestingResultProcessor.processConsentTestingResult(provider, new ConsentTestingResult(ConsentTestingMessage.GENERATED, "SomeUri"), authenticationMeansReference);

        // then
        verify(mockedAppender, times(3)).doAppend(loggingEventCaptor.capture());
        ILoggingEvent log = loggingEventCaptor.getAllValues().get(0);
        assertThat(log.getLevel()).isEqualTo(Level.WARN);
        assertThat(log.getFormattedMessage()).contains(ConsentTestingMessage.GENERATED.getMessage());
        assertThat(log.getFormattedMessage()).contains("SomeUri");
        assertThat(log.getMDCPropertyMap().get(ConsentTestingResultProcessor.CONSENT_TESTING_RESULT_METRIC_NAME)).isEqualTo(ConsentTestingMessage.GENERATED.name());

        Meter.Id consentTestingMetric = meterRegistry.getMeters().get(0).getId();
        assertThat(consentTestingMetric.getName()).isEqualTo(CONSENT_TESTING_RESULT_METRIC_NAME);
        assertThat(consentTestingMetric.getTag(PROVIDER_IDENTIFIER_METRIC_TAG)).isEqualTo("SOME_PROVIDER");
        assertThat(consentTestingMetric.getTag(PROVIDER_SERVICETYPE_METRIC_TAG)).isEqualTo("AIS");
        assertThat(consentTestingMetric.getTag(RESULT_METRIC_TAG)).isEqualTo("failure");
        assertThat(consentTestingMetric.getTag(CLIENT_ID_METRIC_TAG)).isEqualTo(MOCK_CLIENT_ID.toString());
    }

    @Test
    public void shouldLogInfoForSuccessfullyRetrievedConsentPage() {
        //given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(MOCK_CLIENT_ID, MOCK_CLIENT_REDIRECT_ID);

        // when
        consentTestingResultProcessor.processConsentTestingResult(provider, new ConsentTestingResult(ConsentTestingMessage.STATUS_200, "SomeUri"), authenticationMeansReference);

        // then
        verify(mockedAppender, times(3)).doAppend(loggingEventCaptor.capture());
        ILoggingEvent log = loggingEventCaptor.getAllValues().get(0);
        assertThat(log.getLevel()).isEqualTo(Level.WARN);
        assertThat(log.getMDCPropertyMap().get(ConsentTestingResultProcessor.CONSENT_TESTING_RESULT_METRIC_NAME)).isEqualTo(ConsentTestingMessage.STATUS_200.name());

        Meter.Id consentTestingMetric = meterRegistry.getMeters().get(0).getId();
        assertThat(consentTestingMetric.getName()).isEqualTo(CONSENT_TESTING_RESULT_METRIC_NAME);
        assertThat(consentTestingMetric.getTag(PROVIDER_IDENTIFIER_METRIC_TAG)).isEqualTo("SOME_PROVIDER");
        assertThat(consentTestingMetric.getTag(PROVIDER_SERVICETYPE_METRIC_TAG)).isEqualTo("AIS");
        assertThat(consentTestingMetric.getTag(RESULT_METRIC_TAG)).isEqualTo("failure");
        assertThat(consentTestingMetric.getTag(CLIENT_ID_METRIC_TAG)).isEqualTo(MOCK_CLIENT_ID.toString());
    }

    @Test
    public void shouldLogInfoForSuccessfullyValidatedRulesForConsentPage() {
        //given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(MOCK_CLIENT_ID, MOCK_CLIENT_REDIRECT_ID);

        // when
        consentTestingResultProcessor.processConsentTestingResult(provider, new ConsentTestingResult(ConsentTestingMessage.VALIDITY_RULES_CHECKED, "SomeUri"), authenticationMeansReference);

        // then
        verify(mockedAppender, times(3)).doAppend(loggingEventCaptor.capture());
        ILoggingEvent log = loggingEventCaptor.getAllValues().get(0);
        assertThat(log.getLevel()).isEqualTo(Level.INFO);
        assertThat(log.getMDCPropertyMap().get(ConsentTestingResultProcessor.CONSENT_TESTING_RESULT_METRIC_NAME)).isEqualTo(ConsentTestingMessage.VALIDITY_RULES_CHECKED.name());

        Meter.Id consentTestingMetric = meterRegistry.getMeters().get(0).getId();
        assertThat(consentTestingMetric.getName()).isEqualTo(CONSENT_TESTING_RESULT_METRIC_NAME);
        assertThat(consentTestingMetric.getTag(PROVIDER_IDENTIFIER_METRIC_TAG)).isEqualTo("SOME_PROVIDER");
        assertThat(consentTestingMetric.getTag(PROVIDER_SERVICETYPE_METRIC_TAG)).isEqualTo("AIS");
        assertThat(consentTestingMetric.getTag(RESULT_METRIC_TAG)).isEqualTo("success");
        assertThat(consentTestingMetric.getTag(CLIENT_ID_METRIC_TAG)).isEqualTo(MOCK_CLIENT_ID.toString());
    }
}