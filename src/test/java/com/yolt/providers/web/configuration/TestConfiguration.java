package com.yolt.providers.web.configuration;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.web.errorhandling.ExtendedExceptionHandlingService;
import com.yolt.service.starter.web.ErrorHandlingProperties;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.errorhandling.ExceptionHandlingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Clock;

@Configuration
@Slf4j
public class TestConfiguration {

    public static final String JACKSON_OBJECT_MAPPER = "providersJacksonObjectMapperForTests";

    @Bean
    public Clock testClock() {
        return Clock.systemDefaultZone();
    }

    @Bean(JACKSON_OBJECT_MAPPER)
    public ObjectMapper jacksonObjectMapper() {
        return new ApplicationConfiguration().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
    }
}
