package com.yolt.providers.web.clientredirecturl;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Session;
import com.yolt.providers.web.configuration.IntegrationTestContext;
import nl.ing.lovebird.testsupport.cassandra.CassandraHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTestContext
public class ClientRedirectUrlRepositoryIntegrationTest {

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID CLIENT_APPLICATION_ID = UUID.randomUUID();
    private static final String URL = "url";
    private static final String NEW_URL = "newUrl";

    private ClientRedirectUrlRepository repository;

    @Autowired
    private Session session;

    @BeforeEach
    public void setup() {
        repository = new ClientRedirectUrlRepository(session);

        ReflectionTestUtils.setField(repository, "writeConsistency", ConsistencyLevel.ONE);
    }

    @AfterEach
    public void cleanup() {
        CassandraHelper.truncate(session, ClientRedirectUrl.class);
    }

    @Test
    public void shouldSaveClientRedirectUrlForUpsertClientRedirectUrlWithCorrectData() {
        // given
        ClientRedirectUrl clientRedirectUrl = new ClientRedirectUrl(CLIENT_ID, CLIENT_APPLICATION_ID, URL, null);

        // when
        repository.upsertClientRedirectUrl(clientRedirectUrl);

        // then
        Optional<ClientRedirectUrl> obtainedClientRedirectUrl = repository.get(CLIENT_ID, CLIENT_APPLICATION_ID);
        assertThat(obtainedClientRedirectUrl).isPresent();
        assertThat(obtainedClientRedirectUrl.get()).isEqualTo(clientRedirectUrl);
    }

    @Test
    public void shouldUpdateExistingClientRedirectUrlForUpsertClientRedirectUrlWithCorrectData() {
        // given
        ClientRedirectUrl clientRedirectUrl = new ClientRedirectUrl(CLIENT_ID, CLIENT_APPLICATION_ID, URL, null);
        repository.upsertClientRedirectUrl(clientRedirectUrl);
        ClientRedirectUrl updatedClientRedirectUrl = new ClientRedirectUrl(CLIENT_ID, CLIENT_APPLICATION_ID, NEW_URL, Instant.now());

        // when
        repository.upsertClientRedirectUrl(updatedClientRedirectUrl);

        // then
        Optional<ClientRedirectUrl> obtainedClientRedirectUrl = repository.get(CLIENT_ID, CLIENT_APPLICATION_ID);
        assertThat(obtainedClientRedirectUrl).isPresent();
        ClientRedirectUrl result = obtainedClientRedirectUrl.get();
        assertThat(result.getClientId())
                .isEqualTo(clientRedirectUrl.getClientId())
                .isEqualTo(updatedClientRedirectUrl.getClientId());
        assertThat(result.getRedirectUrlId())
                .isEqualTo(clientRedirectUrl.getRedirectUrlId())
                .isEqualTo(updatedClientRedirectUrl.getRedirectUrlId());
        assertThat(result.getUrl())
                .isNotEqualTo(clientRedirectUrl.getUrl())
                .isEqualTo(updatedClientRedirectUrl.getUrl());
        assertThat(result.getUpdated())
                .isEqualTo(updatedClientRedirectUrl.getUpdated().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    public void shouldDeleteExistingClientRedirectUrlForDeleteWithCorrectData() {
        // given
        ClientRedirectUrl clientRedirectUrl = new ClientRedirectUrl(CLIENT_ID, CLIENT_APPLICATION_ID, URL, null);
        repository.upsertClientRedirectUrl(clientRedirectUrl);

        // when
        repository.delete(CLIENT_ID, CLIENT_APPLICATION_ID);

        // then
        Optional<ClientRedirectUrl> obtainedClientRedirectUrl = repository.get(CLIENT_ID, CLIENT_APPLICATION_ID);
        assertThat(obtainedClientRedirectUrl).isNotPresent();
    }
}