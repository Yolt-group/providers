package com.yolt.providers.web.clientredirecturl;

import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import nl.ing.lovebird.cassandra.CassandraRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;

@Repository
public class ClientRedirectUrlRepository extends CassandraRepository<ClientRedirectUrl> {

    @Autowired
    protected ClientRedirectUrlRepository(final Session session) {
        super(session, ClientRedirectUrl.class);
    }

    public void upsertClientRedirectUrl(final ClientRedirectUrl clientRedirectUrl) {
        super.save(clientRedirectUrl);
    }

    public void delete(final UUID clientId, final UUID redirectUrlId) {
        final Delete deleteQuery = createDelete();
        deleteQuery.where(eq(ClientRedirectUrl.CLIENT_ID_COLUMN, clientId))
                .and(eq(ClientRedirectUrl.REDIRECT_URL_ID, redirectUrlId));
        super.executeDelete(deleteQuery);
    }

    public Optional<ClientRedirectUrl> get(final UUID clientId, final UUID redirectUrlId) {
        Select.Where selectQuery = QueryBuilder.select().from(ClientRedirectUrl.TABLE_NAME)
                .where(eq(ClientRedirectUrl.CLIENT_ID_COLUMN, clientId))
                .and(eq(ClientRedirectUrl.REDIRECT_URL_ID, redirectUrlId));
        return selectOne(selectQuery);
    }
}
