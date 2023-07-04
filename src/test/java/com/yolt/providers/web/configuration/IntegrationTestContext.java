package com.yolt.providers.web.configuration;

import com.yolt.providers.web.ProviderApp;
import nl.ing.lovebird.cassandra.test.EnableExternalCassandraTestDatabase;
import nl.ing.lovebird.kafka.test.EnableExternalKafkaTestCluster;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This class is serves a purpose of declaring unified context definition for running integration tests.
 * It's important to use it due to the usage of test-containers, to avoid creating multiple
 * instances of container during tests runs we make use spring test context caching mechanism.
 * The mechanism is fragile and strictly depends on test context definition. Therefore we had to prepare
 * a single definition of test context which really is superset of needed beans and configurations.
 * More information can be found in C4PO-4405
 */

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = {ProviderApp.class, TestConfiguration.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnableExternalCassandraTestDatabase
@EnableExternalKafkaTestCluster
@AutoConfigureWireMock(port = 0)
public @interface IntegrationTestContext {
    // Annotations only
}
