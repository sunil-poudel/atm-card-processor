CREATE TABLE transactions (
    id                     UUID          NOT NULL,
    card_id                UUID          NOT NULL,
    api_client_id          UUID          NULL,
    initiated_by_user_id   UUID          NULL,
    idempotency_scope      VARCHAR(80)   NOT NULL,
    idempotency_key        VARCHAR(64)   NOT NULL,
    request_fingerprint    VARCHAR(64)   NOT NULL,
    amount_minor           BIGINT        NOT NULL,
    currency               VARCHAR(3)    NOT NULL,
    merchant_name          VARCHAR(100)  NOT NULL,
    mcc                    VARCHAR(4)    NOT NULL,
    reference              VARCHAR(64)   NULL,
    status                 VARCHAR(10)   NOT NULL,
    response_code          VARCHAR(2)    NOT NULL,
    decline_reason         VARCHAR(30)   NULL,
    flagged                BOOLEAN       NOT NULL DEFAULT FALSE,
    risk_score             INTEGER       NOT NULL DEFAULT 0,
    balance_after_minor    BIGINT        NULL,
    processing_millis      INTEGER       NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT fk_transactions_card FOREIGN KEY (card_id) REFERENCES cards (id),
    CONSTRAINT fk_transactions_client FOREIGN KEY (api_client_id) REFERENCES api_clients (id),
    CONSTRAINT fk_transactions_user FOREIGN KEY (initiated_by_user_id) REFERENCES app_users (id),
    CONSTRAINT ux_transactions_idempotency UNIQUE (idempotency_scope, idempotency_key),
    CONSTRAINT ck_transactions_status CHECK (status IN ('APPROVED', 'DECLINED')),
    CONSTRAINT ck_transactions_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_transactions_currency CHECK (currency IN ('NPR', 'USD')),
    CONSTRAINT ck_transactions_mcc CHECK (mcc ~ '^[0-9]{4}$'),
    CONSTRAINT ck_transactions_response_code CHECK (response_code ~ '^[0-9]{2}$'),
    CONSTRAINT ck_transactions_risk CHECK (risk_score BETWEEN 0 AND 100),
    CONSTRAINT ck_transactions_fingerprint CHECK (char_length(request_fingerprint) = 64),
    CONSTRAINT ck_transactions_initiator CHECK (
        (api_client_id IS NOT NULL AND initiated_by_user_id IS NULL) OR
        (api_client_id IS NULL AND initiated_by_user_id IS NOT NULL)),
    CONSTRAINT ck_transactions_consistency CHECK (
        (status = 'APPROVED' AND response_code = '00' AND decline_reason IS NULL AND balance_after_minor IS NOT NULL) OR
        (status = 'DECLINED' AND response_code <> '00' AND decline_reason IS NOT NULL AND balance_after_minor IS NULL AND flagged = FALSE))
);
CREATE INDEX ix_transactions_card_time ON transactions (card_id, created_at DESC);
CREATE INDEX ix_transactions_time ON transactions (created_at DESC);
CREATE INDEX ix_transactions_status_code_time ON transactions (status, response_code, created_at DESC);
CREATE INDEX ix_transactions_mcc ON transactions (mcc);
CREATE INDEX ix_transactions_flagged ON transactions (created_at DESC) WHERE flagged = TRUE;

ALTER TABLE ledger_entries
    ADD CONSTRAINT fk_ledger_entries_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id);
