package com.yolt.providers.web.threadinfo;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static com.yolt.providers.web.threadinfo.ThreadInfoService.STARTUP_LOG;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ThreadInfoServiceTest {

    @Captor
    private ArgumentCaptor<ILoggingEvent> captorLoggingEvent;
    @Mock
    private Appender<ILoggingEvent> mockAppender;

    private ThreadInfoService subject;

    @BeforeEach
    void beforeEach() {
        Logger logger = (Logger) LoggerFactory.getLogger(ThreadInfoService.class);
        logger.setLevel(Level.DEBUG);
        logger.addAppender(mockAppender);

        subject = new ThreadInfoService(this::getSampleThreadInfo);
    }

    @AfterEach
    void teardown() {
        Logger logger = (Logger) LoggerFactory.getLogger(ThreadInfo.class);
        logger.detachAppender(mockAppender);
    }


    @Test
    public void shouldLogAllThreadInformationIntoRdd() {
        //when
        subject.logQuasiThreadDump(null);

        //then
        verify(mockAppender, times(3)).doAppend(captorLoggingEvent.capture());
        List<ILoggingEvent> allValues = captorLoggingEvent.getAllValues();
        ILoggingEvent firstEvent = allValues.get(0);
        assertThat(firstEvent.getFormattedMessage()).contains(STARTUP_LOG);
        assertThat(firstEvent.getLevel()).isEqualTo(Level.INFO);

        allValues
                .subList(1, allValues.size())
                .forEach(
                        iLoggingEvent -> {
                            assertThat(iLoggingEvent.getFormattedMessage()).contains("Thread");
                            assertThat(iLoggingEvent.getLevel()).isEqualTo(Level.DEBUG);
                            assertThat(iLoggingEvent.getMarker().toString()).contains("raw-data");
                        }
                );
    }


    @Test
    public void shouldLogFilteredThreadInformationIntoRdd() {
        //when
        subject.logQuasiThreadDump(".*thread-2.*");

        //then
        verify(mockAppender, times(2)).doAppend(captorLoggingEvent.capture());
        List<ILoggingEvent> allValues = captorLoggingEvent.getAllValues();
        ILoggingEvent firstEvent = allValues.get(0);
        assertThat(firstEvent.getFormattedMessage()).contains(STARTUP_LOG);
        assertThat(firstEvent.getLevel()).isEqualTo(Level.INFO);

        List<ILoggingEvent> threadsLoggingEvents = allValues.subList(1, allValues.size());

        ILoggingEvent foundThreadEvent = threadsLoggingEvents.get(0);
        assertThat(foundThreadEvent.getFormattedMessage()).contains("my-thread-2");
        assertThat(foundThreadEvent.getLevel()).isEqualTo(Level.DEBUG);
        assertThat(foundThreadEvent.getMarker().toString()).contains("raw-data");
    }

    private Map<Thread, StackTraceElement[]> getSampleThreadInfo() {
        return Map.of(
                new Thread("my-thread-1"), new StackTraceElement[]{new StackTraceElement("My.class", "func1()", "My.java", 12)},
                new Thread("my-thread-2"), new StackTraceElement[]{new StackTraceElement("My2.class", "func2()", "My2.java", 13)}
        );
    }
}