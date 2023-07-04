package com.yolt.providers.web.authenticationmeans;

import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.cassandra.CassandraRepository;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;

@Slf4j
@Repository
public class ClientRedirectUrlClientConfigurationRepository extends CassandraRepository<InternalClientRedirectUrlClientConfiguration> {

    @Autowired
    public ClientRedirectUrlClientConfigurationRepository(final Session session) {
        super(session, InternalClientRedirectUrlClientConfiguration.class);
    }

    void upsert(InternalClientRedirectUrlClientConfiguration internalClientRedirectUrlAuthenticationMeans) {
        log.info("Saving authentication means for client: {}, redirectUrlId: {}, serviceType: {}, provider: {}",
                internalClientRedirectUrlAuthenticationMeans.getClientId(),
                internalClientRedirectUrlAuthenticationMeans.getRedirectUrlId(),
                internalClientRedirectUrlAuthenticationMeans.getServiceType(),
                internalClientRedirectUrlAuthenticationMeans.getProvider()); // NOSHERIFF we need those details to confirm successful operation and we are in control of those listed here

        super.save(internalClientRedirectUrlAuthenticationMeans);
    }

    public List<InternalClientRedirectUrlClientConfiguration> get(@NonNull UUID clientId, @NonNull UUID redirectUrlId, @Nullable ServiceType serviceType) {
        Select select = QueryBuilder.select().from(InternalClientRedirectUrlClientConfiguration.TABLE_NAME);
        select.where(eq(InternalClientRedirectUrlClientConfiguration.CLIENT_ID_COLUMN, clientId));
        select.where(eq(InternalClientRedirectUrlClientConfiguration.REDIRECT_URL_ID, redirectUrlId));
        if (serviceType != null) {
            select.where(eq(InternalClientRedirectUrlClientConfiguration.SERVICE_TYPE_COLUMN, serviceType));
        }
        return select(select);
    }

    public Optional<InternalClientRedirectUrlClientConfiguration> get(UUID clientId, UUID redirectUrlId, ServiceType serviceType, String provider) {
        Select select = QueryBuilder.select().from(InternalClientRedirectUrlClientConfiguration.TABLE_NAME);
        select.where(eq(InternalClientRedirectUrlClientConfiguration.CLIENT_ID_COLUMN, clientId));
        select.where(eq(InternalClientRedirectUrlClientConfiguration.REDIRECT_URL_ID, redirectUrlId));
        select.where(eq(InternalClientRedirectUrlClientConfiguration.SERVICE_TYPE_COLUMN, serviceType));
        select.where(eq(InternalClientRedirectUrlClientConfiguration.PROVIDER_COLUMN, provider));
        return selectOne(select);
    }

    public void delete(UUID clientId, UUID redirectUrlId, ServiceType serviceType, String provider) {
        final Delete deleteQuery = createDelete();
        deleteQuery.where(eq(InternalClientRedirectUrlClientConfiguration.CLIENT_ID_COLUMN, clientId))
                .and(eq(InternalClientRedirectUrlClientConfiguration.REDIRECT_URL_ID, redirectUrlId))
                .and(eq(InternalClientRedirectUrlClientConfiguration.SERVICE_TYPE_COLUMN, serviceType))
                .and(eq(InternalClientRedirectUrlClientConfiguration.PROVIDER_COLUMN, provider));
        super.executeDelete(deleteQuery);
    }

    public void delete(UUID clientId, UUID redirectUrlId) {
        final Delete deleteQuery = createDelete();
        deleteQuery.where(eq(InternalClientRedirectUrlClientConfiguration.CLIENT_ID_COLUMN, clientId))
                .and(eq(InternalClientRedirectUrlClientConfiguration.REDIRECT_URL_ID, redirectUrlId));
        super.executeDelete(deleteQuery);
    }

    public List<InternalClientRedirectUrlClientConfiguration> getAll() {
        return select(QueryBuilder.select().from(InternalClientRedirectUrlClientConfiguration.TABLE_NAME));
    }
}
