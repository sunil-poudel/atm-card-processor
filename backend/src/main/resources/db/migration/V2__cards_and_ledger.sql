CREATE TABLE cards (
    id                          UUID         NOT NULL,
    pan_encrypted               VARCHAR(300) NOT NULL,
    pan_key_id                  VARCHAR(20)  NOT NULL,
    pan_hash                    VARCHAR(64)  NOT NULL,
    bin                         VARCHAR(6)   NOT NULL,
    last4                       VARCHAR(4)   NOT NULL,
    holder_name                 VARCHAR(100) NOT NULL,
    expiry_month                INTEGER      NOT NULL,
    expiry_year                 INTEGER      NOT NULL,
    currency                    VARCHAR(3)   NOT NULL,
    status                      VARCHAR(10)  NOT NULL,
    balance_minor               BIGINT       NOT NULL DEFAULT 0,
    per_transaction_limit_minor BIGINT       NOT NULL,
    daily_limit_minor           BIGINT       NOT NULL,
    block_reason                VARCHAR(200) NULL,
    activated_at                TIMESTAMPTZ  NULL,
    blocked_at                  TIMESTAMPTZ  NULL,
    closed_at                   TIMESTAMPTZ  NULL,
    created_by                  UUID         NULL,
    created_at                  TIMESTAMPTZ  NOT NULL,
    updated_at                  TIMESTAMPTZ  NOT NULL,
    version                     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_cards PRIMARY KEY (id),
    CONSTRAINT fk_cards_created_by FOREIGN KEY (created_by) REFERENCES app_users (id),
    CONSTRAINT ux_cards_pan_hash UNIQUE (pan_hash),
    CONSTRAINT ck_cards_status CHECK (status IN ('INACTIVE', 'ACTIVE', 'BLOCKED', 'CLOSED')),
    CONSTRAINT ck_cards_currency CHECK (currency IN ('NPR', 'USD')),
    CONSTRAINT ck_cards_bin CHECK (bin ~ '^[0-9]{6}$'),
    CONSTRAINT ck_cards_last4 CHECK (last4 ~ '^[0-9]{4}$'),
    CONSTRAINT ck_cards_expiry_month CHECK (expiry_month BETWEEN 1 AND 12),
    CONSTRAINT ck_cards_expiry_year CHECK (expiry_year BETWEEN 2000 AND 2100),
    CONSTRAINT ck_cards_balance CHECK (balance_minor >= 0),
    CONSTRAINT ck_cards_per_txn_limit CHECK (per_transaction_limit_minor > 0),
    CONSTRAINT ck_cards_daily_limit CHECK (daily_limit_minor >= per_transaction_limit_minor)
);
CREATE INDEX ix_cards_status ON cards (status);
CREATE INDEX ix_cards_created_at ON cards (created_at DESC);
CREATE INDEX ix_cards_holder_lower ON cards (lower(holder_name));
CREATE INDEX ix_cards_last4 ON cards (last4);

CREATE TABLE card_blocked_mcc (
    card_id UUID       NOT NULL,
    mcc     VARCHAR(4) NOT NULL,
    CONSTRAINT pk_card_blocked_mcc PRIMARY KEY (card_id, mcc),
    CONSTRAINT fk_card_blocked_mcc_card FOREIGN KEY (card_id) REFERENCES cards (id),
    CONSTRAINT ck_card_blocked_mcc_mcc CHECK (mcc ~ '^[0-9]{4}$')
);

-- Created in V2 (not V3) because ledger rows reference disputes and transactions by id only
-- through nullable columns whose foreign keys are added in V5 once those tables exist.
CREATE TABLE ledger_entries (
    id                   UUID          NOT NULL,
    card_id              UUID          NOT NULL,
    entry_type           VARCHAR(20)   NOT NULL,
    amount_minor         BIGINT        NOT NULL,
    balance_after_minor  BIGINT        NOT NULL,
    transaction_id       UUID          NULL,
    dispute_id           UUID          NULL,
    description          VARCHAR(200)  NULL,
    created_by           VARCHAR(100)  NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_ledger_entries PRIMARY KEY (id),
    CONSTRAINT fk_ledger_entries_card FOREIGN KEY (card_id) REFERENCES cards (id),
    CONSTRAINT ck_ledger_entries_type CHECK (entry_type IN ('TOPUP', 'PURCHASE', 'DISPUTE_CREDIT')),
    CONSTRAINT ck_ledger_entries_amount CHECK (amount_minor <> 0),
    CONSTRAINT ck_ledger_entries_balance CHECK (balance_after_minor >= 0),
    CONSTRAINT ck_ledger_entries_sign CHECK (
        (entry_type = 'PURCHASE' AND amount_minor < 0) OR
        (entry_type IN ('TOPUP', 'DISPUTE_CREDIT') AND amount_minor > 0)),
    CONSTRAINT ck_ledger_entries_refs CHECK (
        (entry_type = 'PURCHASE' AND transaction_id IS NOT NULL AND dispute_id IS NULL) OR
        (entry_type = 'DISPUTE_CREDIT' AND dispute_id IS NOT NULL AND transaction_id IS NULL) OR
        (entry_type = 'TOPUP' AND transaction_id IS NULL AND dispute_id IS NULL))
);
CREATE INDEX ix_ledger_entries_card_time ON ledger_entries (card_id, created_at DESC);
CREATE UNIQUE INDEX ux_ledger_entries_transaction ON ledger_entries (transaction_id) WHERE transaction_id IS NOT NULL;
CREATE UNIQUE INDEX ux_ledger_entries_dispute ON ledger_entries (dispute_id) WHERE dispute_id IS NOT NULL;
