package com.yolt.providers.web.clientredirecturl;

import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = ClientRedirectUrl.TABLE_NAME)
public class ClientRedirectUrl {

    public static final String TABLE_NAME = "client_redirect_url";
    public static final String CLIENT_ID_COLUMN = "client_id";
    public static final String REDIRECT_URL_ID = "redirect_url_id";
    public static final String REDIRECT_URL_COLUMN = "base_redirect_url";
    public static final String UPDATED_COLUMN = "updated";

    @PartitionKey(0)
    @NotNull
    @Column(name = CLIENT_ID_COLUMN)
    protected UUID clientId;

    @PartitionKey(1)
    @NotNull
    @Column(name = REDIRECT_URL_ID)
    protected UUID redirectUrlId;

    @URL
    @NotNull
    @Column(name = REDIRECT_URL_COLUMN)
    protected String url;

    @Column(name = UPDATED_COLUMN)
    protected Instant updated;

}
