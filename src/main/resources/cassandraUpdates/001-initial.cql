CREATE TABLE IF NOT EXISTS client_application_provider_authentication_means (
    client_id uuid,
    client_application_id uuid,
    provider text,
    authentication_means text,
    updated timestamp,
    PRIMARY KEY ((client_id, client_application_id), provider)
);

CREATE TABLE IF NOT EXISTS client_application (
    client_id uuid,
    client_application_id uuid,
    base_redirect_url text,
    updated timestamp,
    PRIMARY KEY ((client_id), client_application_id)
);

INSERT INTO client_application (client_id, client_application_id, base_redirect_url, updated) VALUES (297ecda4-fd60-4999-8575-b25ad23b249c, cee03d67-664c-45d1-b84d-eb042d88ce65, 'https://www.yolt.com/callback', '2018-06-21 12:00:00');
INSERT INTO client_application (client_id, client_application_id, base_redirect_url, updated) VALUES (a5154eb9-9f47-43b4-81b1-fce67813c002, 82a19af9-dd8f-4d26-92e4-38e71f4aef67, 'https://ingfrance.notimplemented.fr', '2018-06-21 12:00:00');
INSERT INTO client_application (client_id, client_application_id, base_redirect_url, updated) VALUES (a2034b12-7dcc-11e8-adc0-fa7ae01bbebc, 4c58a5cc-7de0-11e8-adc0-fa7ae01bbebc, 'https://www.yolt.notimplemented.com/callback', '2018-06-21 12:00:00');
INSERT INTO client_application (client_id, client_application_id, base_redirect_url, updated) VALUES (a2034b12-7dcc-11e8-adc0-fa7ae01bbebc, 29e085b0-7dd0-11e8-adc0-fa7ae01bbebc, 'https://www.yolttestclient.com/callback', '2018-06-21 12:00:00');
