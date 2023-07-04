package com.yolt.providers.web.authenticationmeans;

import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.cassandra.CassandraRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;

@Slf4j
@Repository
class ClientAuthenticationMeansRepository extends CassandraRepository<InternalClientAuthenticationMeans> {

    @Autowired
    public ClientAuthenticationMeansRepository(final Session session) {
        super(session, InternalClientAuthenticationMeans.class);
    }


    public List<InternalClientAuthenticationMeans> list() {
        return select(QueryBuilder.select().from(InternalClientAuthenticationMeans.TABLE_NAME));
    }

    @Override
    public void save(InternalClientAuthenticationMeans internalClientAuthenticationMeans) {
        /*
         * Depending on experience with logging clientId and provider name, we are pretty confident,
         * that we do not log anything sensitive
         */
        log.info("Saving authentication means for client: {}, provider: {}",
                internalClientAuthenticationMeans.getClientId(),
                internalClientAuthenticationMeans.getProvider()); //NOSHERIFF Provider is our own value, that we are in control.

        super.save(internalClientAuthenticationMeans);
    }

    public Stream<InternalClientAuthenticationMeans> get(UUID clientId) {
        Select select = QueryBuilder.select().from(InternalClientAuthenticationMeans.TABLE_NAME);
        select.where(eq(InternalClientAuthenticationMeans.CLIENT_ID_COLUMN, clientId));
        return select(select).stream();
    }

    public Optional<InternalClientAuthenticationMeans> get(UUID clientId, String provider) {
        Select select = QueryBuilder.select().from(InternalClientAuthenticationMeans.TABLE_NAME);
        select.where(eq(InternalClientAuthenticationMeans.CLIENT_ID_COLUMN, clientId));
        select.where(eq(InternalClientAuthenticationMeans.PROVIDER_COLUMN, provider));
        return selectOne(select);
    }

    public void delete(UUID clientId, String provider) {
        final Delete deleteQuery = createDelete();
        deleteQuery.where(eq(InternalClientAuthenticationMeans.CLIENT_ID_COLUMN, clientId))
                .and(eq(InternalClientAuthenticationMeans.PROVIDER_COLUMN, provider));
        super.executeDelete(deleteQuery);
    }

    public void delete(UUID clientId) {
        final Delete deleteQuery = createDelete();
        deleteQuery.where(eq(InternalClientAuthenticationMeans.CLIENT_ID_COLUMN, clientId));
        super.executeDelete(deleteQuery);
    }
}
