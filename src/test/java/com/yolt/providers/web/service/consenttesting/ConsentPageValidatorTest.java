package com.yolt.providers.web.service.consenttesting;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.yolt.providers.common.domain.consenttesting.ConsentValidityRules;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.web.service.domain.ConsentTestingMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentPageValidatorTest {

    private static final String SOME_URL_DECODED = "http://somepage.com?redirectUri=https://www.yolt.com/callback-dev";
    private static final String SOME_BANK_CONSENT_PAGE = "<!DOCTYPE html><html><body><h2>Bank login page</h2><form>" +
            "<label>Login:</label><input type=\"text\"><label>Password:</label>" +
            "<input type=\"text\"><input type=\"submit\" value=\"Submit\"></form> </body></html>";
    private static final String SOME_URL = "http://somepage.com?redirectUri=https%3A%2F%2Fwww.yolt.com%2Fcallback-dev";

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private Provider provider;
    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @Captor
    private ArgumentCaptor<ILoggingEvent> captorLoggingEvent;

    @InjectMocks
    private ConsentPageValidator sut;

    @Test
    void shouldReturnStatus200EmptyValidityRulesWhenConsentPageWillBeSuccessfullyFetchedAndRuleSetWillBeEmpty() {
        //given
        when(restTemplate.exchange(eq(SOME_URL_DECODED), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(SOME_BANK_CONSENT_PAGE));
        when(provider.getConsentValidityRules())
                .thenReturn(ConsentValidityRules.EMPTY_RULES_SET);

        //when
        ConsentTestingMessage result = sut.retrieveAndValidateConsentPage(SOME_URL, restTemplate, provider);

        //then
        assertThat(result).isEqualTo(ConsentTestingMessage.STATUS_200_EMPTY_VALIDITY_RULES);
    }

    @Test
    void shouldReturnValidityRulesCheckedStatusWhenConsentPageWillBeSuccessfullyFetchedAndRuleSetWillBeMatched() {
        //given
        Set<String> keywords = new HashSet<>();
        keywords.add("Login:");
        keywords.add("Password:");
        keywords.add("Submit");
        when(restTemplate.exchange(eq(SOME_URL_DECODED), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(SOME_BANK_CONSENT_PAGE));
        when(provider.getConsentValidityRules())
                .thenReturn(new ConsentValidityRules(keywords));

        //when
        ConsentTestingMessage result = sut.retrieveAndValidateConsentPage(SOME_URL, restTemplate, provider);

        //then
        assertThat(result).isEqualTo(ConsentTestingMessage.VALIDITY_RULES_CHECKED);
    }

    @Test
    void shouldReturnResponse200StatusWhenConsentPageWillNotBeMatchedWithKeywords() {
        //given
        Set<String> keywords = new HashSet<>();
        keywords.add("Login:");
        keywords.add("Password:");
        keywords.add("Some missing keyword");
        when(restTemplate.exchange(eq(SOME_URL_DECODED), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(SOME_BANK_CONSENT_PAGE));
        when(provider.getConsentValidityRules())
                .thenReturn(new ConsentValidityRules(keywords));

        //when
        ConsentTestingMessage result = sut.retrieveAndValidateConsentPage(SOME_URL, restTemplate, provider);

        //then
        assertThat(result).isEqualTo(ConsentTestingMessage.STATUS_200);
    }

    @Test
    void shouldReturnGeneratedStatusWhenConsentPageWillNotBeSuccessfullyFetched() {
        //given
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(eq(SOME_URL_DECODED), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        //when
        ConsentTestingMessage result = sut.retrieveAndValidateConsentPage(SOME_URL, restTemplate, provider);

        //then
        assertThat(result).isEqualTo(ConsentTestingMessage.GENERATED);
    }

    @Test
    void shouldLogProperlyWhenHttpStatusExceptionOccursDuringConsentPageRetrieval() {
        //given
        Logger logger = (Logger) LoggerFactory.getLogger(ConsentPageValidator.class);
        logger.setLevel(Level.ALL);
        logger.addAppender(mockAppender);

        when(restTemplate.exchange(eq(SOME_URL_DECODED), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        //when
        sut.retrieveAndValidateConsentPage(SOME_URL, restTemplate, provider);

        //then
        verify(mockAppender, times(3)).doAppend(captorLoggingEvent.capture());

        List<ILoggingEvent> allLogs = captorLoggingEvent.getAllValues();

        ILoggingEvent logContainingStatusAndUrl = allLogs.get(1);
        assertThat(logContainingStatusAndUrl.getFormattedMessage()).contains(SOME_URL_DECODED)
                .contains(HttpStatus.BAD_REQUEST.toString());

        ILoggingEvent exceptionLog = allLogs.get(2);
        assertThat(exceptionLog.getMessage()).contains("An exception occurred during consent page retrieval");
    }
}
