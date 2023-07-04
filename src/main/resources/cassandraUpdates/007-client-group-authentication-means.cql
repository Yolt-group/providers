CREATE TABLE IF NOT EXISTS client_group_redirect_url_client_configuration (
    client_group_id uuid,
    redirect_url_id uuid,
    service_type text,
    provider text,
    authentication_means text,
    updated timestamp,
    PRIMARY KEY ((client_group_id), redirect_url_id, service_type, provider)
);