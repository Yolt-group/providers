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
public class ClientGroupRedirectUrlClientConfigurationRepository extends CassandraRepository<InternalClientGroupRedirectUrlClientConfiguration> {

    @Autowired
    public ClientGroupRedirectUrlClientConfigurationRepository(final Session session) {
        super(session, InternalClientGroupRedirectUrlClientConfiguration.class);
    }

    void upsert(InternalClientGroupRedirectUrlClientConfiguration internalClientGroupRedirectUrlClientConfiguration) {
        log.info("Saving authentication means for client-group: {}, redirectUrlId: {}, serviceType: {}, provider: {}",
                internalClientGroupRedirectUrlClientConfiguration.getClientGroupId(),
                internalClientGroupRedirectUrlClientConfiguration.getRedirectUrlId(),
                internalClientGroupRedirectUrlClientConfiguration.getServiceType(),
                internalClientGroupRedirectUrlClientConfiguration.getProvider()); // NOSHERIFF we need those details to confirm successful operation and we are in control of those listed here

        super.save(internalClientGroupRedirectUrlClientConfiguration);
    }

    public List<InternalClientGroupRedirectUrlClientConfiguration> list() {
        return select(QueryBuilder.select().from(InternalClientGroupRedirectUrlClientConfiguration.TABLE_NAME));
    }

    public List<InternalClientGroupRedirectUrlClientConfiguration> get(@NonNull UUID clientGroupId, @NonNull UUID redirectUrlId, @Nullable ServiceType serviceType) {
        Select select = QueryBuilder.select().from(InternalClientGroupRedirectUrlClientConfiguration.TABLE_NAME);
        select.where(eq(InternalClientGroupRedirectUrlClientConfiguration.CLIENT_GROUP_ID_COLUMN, clientGroupId));
        select.where(eq(InternalClientGroupRedirectUrlClientConfiguration.REDIRECT_URL_ID, redirectUrlId));
        if (serviceType != null) {
            select.where(eq(InternalClientGroupRedirectUrlClientConfiguration.SERVICE_TYPE_COLUMN, serviceType));
        }
        return select(select);
    }

    public Optional<InternalClientGroupRedirectUrlClientConfiguration> get(UUID clientGroupId, UUID redirectUrlId, ServiceType serviceType, String provider) {
        Select select = QueryBuilder.select().from(InternalClientGroupRedirectUrlClientConfiguration.TABLE_NAME);
        select.where(eq(InternalClientGroupRedirectUrlClientConfiguration.CLIENT_GROUP_ID_COLUMN, clientGroupId));
        select.where(eq(InternalClientGroupRedirectUrlClientConfiguration.REDIRECT_URL_ID, redirectUrlId));
        select.where(eq(InternalClientGroupRedirectUrlClientConfiguration.SERVICE_TYPE_COLUMN, serviceType));
        select.where(eq(InternalClientGroupRedirectUrlClientConfiguration.PROVIDER_COLUMN, provider));
        return selectOne(select);
    }

    public void delete(UUID clientGroupId, UUID redirectUrlId, ServiceType serviceType, String provider) {
        final Delete deleteQuery = createDelete();
        deleteQuery.where(eq(InternalClientGroupRedirectUrlClientConfiguration.CLIENT_GROUP_ID_COLUMN, clientGroupId))
                .and(eq(InternalClientGroupRedirectUrlClientConfiguration.REDIRECT_URL_ID, redirectUrlId))
                .and(eq(InternalClientGroupRedirectUrlClientConfiguration.SERVICE_TYPE_COLUMN, serviceType))
                .and(eq(InternalClientGroupRedirectUrlClientConfiguration.PROVIDER_COLUMN, provider));
        super.executeDelete(deleteQuery);
    }

    public void delete(UUID clientGroupId, UUID redirectUrlId) {
        final Delete deleteQuery = createDelete();
        deleteQuery.where(eq(InternalClientGroupRedirectUrlClientConfiguration.CLIENT_GROUP_ID_COLUMN, clientGroupId))
                .and(eq(InternalClientGroupRedirectUrlClientConfiguration.REDIRECT_URL_ID, redirectUrlId));
        super.executeDelete(deleteQuery);
    }
}
