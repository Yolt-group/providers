package com.yolt.providers.web.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.web.cryptography.YoltSecurityProvider;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.metrics.web.servlet.DefaultWebMvcTagsProvider;
import org.springframework.boot.actuate.metrics.web.servlet.WebMvcTagsProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.HandlerMapping;

import javax.net.ssl.SSLSocketFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Security;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@EnableAsync
@Configuration
@ComponentScan("com.yolt.providers")
public class ApplicationConfiguration {

    public static final String ASYNC_PROVIDER_FETCH_DATA_EXECUTOR = "asyncProviderFetchDataExecutor";
    public static final String ASYNC_PROVIDER_FETCH_EXTERNAL_USER_IDS_EXECUTOR = "asyncProviderFetchExternalUserIdsExecutor";
    public static final String ASYNC_PROVIDER_CONSENT_TESTER_EXECUTOR = "asyncProviderConsentTesterExecutor";
    public static final String OBJECT_MAPPER = "providersObjectMapper";

    private static final int DEFAULT_THREAD_POOL_SIZE = 200;

    public static void ensureSecurityProvidersLoaded() {
        // We need to insert the YoltSecurityProvider before SunRSA, but after the generic Sun JCE provider. See YoltSecurityProvider.
        Security.insertProviderAt(new YoltSecurityProvider(), 2);
        Security.addProvider(new BouncyCastleProvider());
    }

    static {
        ensureSecurityProvidersLoaded();
    }

    private static final String PROVIDER_TAG = "provider";
    private static final String SITE_ID_TAG = "site_id";

    private final int defaultThreadPoolSize;

    public ApplicationConfiguration() {
        this(DEFAULT_THREAD_POOL_SIZE);
    }

    @Autowired
    public ApplicationConfiguration(@Value("${lovebird.providers.threadpool.default.size:" + DEFAULT_THREAD_POOL_SIZE + "}") final int defaultThreadPoolSize) {
        this.defaultThreadPoolSize = defaultThreadPoolSize;
    }

    /**
     * From TaskExecutionAutoConfiguration. Ensures that there is also a default task executor for
     * any components that might need it.
     */
    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    @ConditionalOnMissingBean({Executor.class})
    public ThreadPoolTaskExecutor applicationTaskExecutor(TaskExecutorBuilder builder) {
        return builder.build();
    }

    @Bean(ASYNC_PROVIDER_FETCH_DATA_EXECUTOR)
    public ThreadPoolTaskExecutor asyncProviderFetchDataExecutor(TaskExecutorBuilder builder) {
        return builder
                .corePoolSize(10)
                .maxPoolSize(defaultThreadPoolSize)
                .queueCapacity(0)
                .threadNamePrefix("async-fetch-data-")
                .build();
    }

    @Bean(ASYNC_PROVIDER_FETCH_EXTERNAL_USER_IDS_EXECUTOR)
    public ThreadPoolTaskExecutor asyncProviderFetchExternalUserIdsExecutor(TaskExecutorBuilder builder) {
        return builder
                .corePoolSize(1)
                .maxPoolSize(1)
                .queueCapacity(1)
                .threadNamePrefix(ASYNC_PROVIDER_FETCH_EXTERNAL_USER_IDS_EXECUTOR + "-")
                .build();
    }

    @Bean(ASYNC_PROVIDER_CONSENT_TESTER_EXECUTOR)
    public ThreadPoolTaskExecutor asyncProviderConsentTesterExecutor(TaskExecutorBuilder builder) {
        return builder
                .corePoolSize(8)
                .maxPoolSize(8)
                .queueCapacity(200)
                .threadNamePrefix(ASYNC_PROVIDER_CONSENT_TESTER_EXECUTOR + "-")
                .build();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public WebMvcTagsProvider webMvcTagsProvider() {
        return new DefaultWebMvcTagsProvider() {
            @Override
            public Iterable<Tag> getTags(final HttpServletRequest request, final HttpServletResponse response, final Object handler, final Throwable exception) {
                Map<String, String> attributes = (Map) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                String providerKeyOrNone = attributes != null && attributes.get(PROVIDER_TAG) != null ? attributes.get(PROVIDER_TAG) : "";
                String siteIdOrNone = request.getHeader(SITE_ID_TAG) != null ? request.getHeader(SITE_ID_TAG) : "";

                return Tags.of(super.getTags(request, response, handler, exception))
                        .and(Tag.of(PROVIDER_TAG, providerKeyOrNone))
                        .and(Tag.of(SITE_ID_TAG, siteIdOrNone));
            }
        };
    }

    @Bean(OBJECT_MAPPER)
    @Primary
    public ObjectMapper jacksonObjectMapper(final Jackson2ObjectMapperBuilder builder) {
        return builder.createXmlMapper(false).build();
    }

}
