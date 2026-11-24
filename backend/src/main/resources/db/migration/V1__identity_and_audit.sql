CREATE TABLE app_users (
    id                    UUID         NOT NULL,
    email                 VARCHAR(254) NOT NULL,
    display_name          VARCHAR(100) NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    role                  VARCHAR(20)  NOT NULL,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ  NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_app_users PRIMARY KEY (id),
    CONSTRAINT ck_app_users_role CHECK (role IN ('ADMIN', 'ANALYST', 'DISPUTE_OFFICER')),
    CONSTRAINT ck_app_users_failed CHECK (failed_login_attempts >= 0)
);
CREATE UNIQUE INDEX ux_app_users_email ON app_users (lower(email));

CREATE TABLE refresh_tokens (
    id             UUID         NOT NULL,
    user_id        UUID         NOT NULL,
    family_id      UUID         NOT NULL,
    token_hash     VARCHAR(64)  NOT NULL,
    issued_at      TIMESTAMPTZ  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ  NULL,
    replaced_by_id UUID         NULL,
    user_agent     VARCHAR(200) NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES app_users (id),
    CONSTRAINT fk_refresh_tokens_replaced FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens (id),
    CONSTRAINT ux_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_hash CHECK (char_length(token_hash) = 64)
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE api_clients (
    id           UUID         NOT NULL,
    name         VARCHAR(100) NOT NULL,
    key_prefix   VARCHAR(12)  NOT NULL,
    key_hash     VARCHAR(64)  NOT NULL,
    status       VARCHAR(10)  NOT NULL,
    created_by   UUID         NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ  NULL,
    last_used_at TIMESTAMPTZ  NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_api_clients PRIMARY KEY (id),
    CONSTRAINT fk_api_clients_created_by FOREIGN KEY (created_by) REFERENCES app_users (id),
    CONSTRAINT ux_api_clients_name UNIQUE (name),
    CONSTRAINT ux_api_clients_prefix UNIQUE (key_prefix),
    CONSTRAINT ck_api_clients_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_api_clients_hash CHECK (char_length(key_hash) = 64)
);

CREATE TABLE audit_events (
    id           UUID          NOT NULL,
    occurred_at  TIMESTAMPTZ   NOT NULL,
    actor_type   VARCHAR(12)   NOT NULL,
    actor_id     UUID          NULL,
    actor_label  VARCHAR(100)  NOT NULL,
    action       VARCHAR(60)   NOT NULL,
    entity_type  VARCHAR(40)   NULL,
    entity_id    UUID          NULL,
    request_id   VARCHAR(64)   NULL,
    ip_address   VARCHAR(45)   NULL,
    details      VARCHAR(1000) NULL,
    CONSTRAINT pk_audit_events PRIMARY KEY (id),
    CONSTRAINT ck_audit_events_actor_type CHECK (actor_type IN ('USER', 'API_CLIENT', 'SYSTEM', 'ANONYMOUS'))
);
CREATE INDEX ix_audit_events_time ON audit_events (occurred_at DESC);
CREATE INDEX ix_audit_events_entity ON audit_events (entity_type, entity_id);
CREATE INDEX ix_audit_events_action ON audit_events (action, occurred_at DESC);
