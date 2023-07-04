CREATE TABLE IF NOT EXISTS client_redirect_url (
    client_id uuid,
    redirect_url_id uuid,
    base_redirect_url text,
    updated timestamp,
    PRIMARY KEY ((client_id), redirect_url_id)
);


CREATE TABLE IF NOT EXISTS client_redirect_url_authentication_means (
    client_id uuid,
    redirect_url_id uuid,
    provider text,
    authentication_means text,
    updated timestamp,
    PRIMARY KEY ((client_id, redirect_url_id), provider)
);

CREATE TABLE IF NOT EXISTS client_authentication_means (
    client_id uuid,
    provider text,
    authentication_means text,
    updated timestamp,
    PRIMARY KEY ((client_id), provider)
);


