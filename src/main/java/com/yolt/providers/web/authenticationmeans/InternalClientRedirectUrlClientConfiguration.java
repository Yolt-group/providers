package com.yolt.providers.web.authenticationmeans;

import com.datastax.driver.mapping.annotations.ClusteringColumn;
import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import nl.ing.lovebird.providerdomain.ServiceType;

import java.time.Instant;
import java.util.UUID;

/**
 * Internal ClientRedirectUrlProviderAuthenticationMeans.
 * This internal class has an encrypted authenticationMeans (thus, not typed).
 * Unfortunately, this class should be public for the datastax driver, so don't use this class other than IN the repository.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = InternalClientRedirectUrlClientConfiguration.TABLE_NAME)
public class InternalClientRedirectUrlClientConfiguration {

    public static final String TABLE_NAME = "client_redirect_url_client_configuration";
    public static final String PROVIDER_COLUMN = "provider";
    public static final String SERVICE_TYPE_COLUMN = "service_type";
    public static final String AUTHENTICATION_MEANS_COLUMN = "authentication_means";
    public static final String CLIENT_ID_COLUMN = "client_id";
    public static final String REDIRECT_URL_ID = "redirect_url_id";
    public static final String UPDATED_COLUMN = "updated";

    @PartitionKey
    @Column(name = CLIENT_ID_COLUMN)
    private UUID clientId;

    @ClusteringColumn
    @Column(name = REDIRECT_URL_ID)
    private UUID redirectUrlId;

    @ClusteringColumn(1)
    @Column(name = SERVICE_TYPE_COLUMN)
    private ServiceType serviceType;

    @ClusteringColumn(2)
    @Column(name = PROVIDER_COLUMN)
    private String provider;

    @Column(name = AUTHENTICATION_MEANS_COLUMN)
    private String authenticationMeans;

    @Column(name = UPDATED_COLUMN)
    private Instant updated;
}
