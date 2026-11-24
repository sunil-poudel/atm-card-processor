CREATE TABLE disputes (
    id               UUID          NOT NULL,
    transaction_id   UUID          NOT NULL,
    card_id          UUID          NOT NULL,
    reason           VARCHAR(30)   NOT NULL,
    description      VARCHAR(1000) NULL,
    status           VARCHAR(30)   NOT NULL,
    amount_minor     BIGINT        NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    opened_by        VARCHAR(100)  NOT NULL,
    opened_at        TIMESTAMPTZ   NOT NULL,
    evidence_due_at  TIMESTAMPTZ   NULL,
    resolved_at      TIMESTAMPTZ   NULL,
    resolved_by      VARCHAR(100)  NULL,
    resolution_note  VARCHAR(1000) NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_disputes PRIMARY KEY (id),
    CONSTRAINT fk_disputes_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT fk_disputes_card FOREIGN KEY (card_id) REFERENCES cards (id),
    CONSTRAINT ux_disputes_transaction UNIQUE (transaction_id),
    CONSTRAINT ck_disputes_status CHECK (status IN
        ('OPENED', 'EVIDENCE_REQUESTED', 'UNDER_REVIEW', 'RESOLVED_WON', 'RESOLVED_LOST')),
    CONSTRAINT ck_disputes_reason CHECK (reason IN
        ('UNAUTHORIZED_TRANSACTION', 'DUPLICATE_CHARGE', 'GOODS_NOT_RECEIVED', 'INCORRECT_AMOUNT', 'OTHER')),
    CONSTRAINT ck_disputes_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_disputes_resolved CHECK (
        (status IN ('RESOLVED_WON', 'RESOLVED_LOST') AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL) OR
        (status NOT IN ('RESOLVED_WON', 'RESOLVED_LOST') AND resolved_at IS NULL))
);
CREATE INDEX ix_disputes_status_due ON disputes (status, evidence_due_at);
CREATE INDEX ix_disputes_card ON disputes (card_id);

CREATE TABLE dispute_events (
    id          UUID          NOT NULL,
    dispute_id  UUID          NOT NULL,
    from_status VARCHAR(30)   NULL,
    to_status   VARCHAR(30)   NOT NULL,
    actor       VARCHAR(100)  NOT NULL,
    note        VARCHAR(1000) NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_dispute_events PRIMARY KEY (id),
    CONSTRAINT fk_dispute_events_dispute FOREIGN KEY (dispute_id) REFERENCES disputes (id)
);
CREATE INDEX ix_dispute_events_dispute ON dispute_events (dispute_id, created_at);

ALTER TABLE ledger_entries
    ADD CONSTRAINT fk_ledger_entries_dispute FOREIGN KEY (dispute_id) REFERENCES disputes (id);
