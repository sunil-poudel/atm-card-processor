CREATE TABLE fraud_rules (
    id              UUID          NOT NULL,
    name            VARCHAR(100)  NOT NULL,
    description     VARCHAR(300)  NULL,
    rule_type       VARCHAR(20)   NOT NULL,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    threshold_value BIGINT        NULL,
    window_seconds  INTEGER       NULL,
    mcc_codes       VARCHAR(300)  NULL,
    score_points    INTEGER       NOT NULL,
    action          VARCHAR(10)   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_fraud_rules PRIMARY KEY (id),
    CONSTRAINT ck_fraud_rules_type CHECK (rule_type IN ('MAX_AMOUNT', 'VELOCITY', 'BLOCKED_MCC')),
    CONSTRAINT ck_fraud_rules_action CHECK (action IN ('FLAG', 'DECLINE')),
    CONSTRAINT ck_fraud_rules_points CHECK (score_points BETWEEN 1 AND 100),
    -- NULL-safe on purpose: a CHECK that evaluates to NULL passes, so every nullable column is tested explicitly.
    CONSTRAINT ck_fraud_rules_params CHECK (
        (rule_type = 'MAX_AMOUNT'  AND threshold_value IS NOT NULL AND threshold_value > 0
                                   AND window_seconds IS NULL AND mcc_codes IS NULL) OR
        (rule_type = 'VELOCITY'    AND threshold_value IS NOT NULL AND threshold_value >= 1
                                   AND window_seconds IS NOT NULL AND window_seconds BETWEEN 1 AND 86400
                                   AND mcc_codes IS NULL) OR
        (rule_type = 'BLOCKED_MCC' AND threshold_value IS NULL AND window_seconds IS NULL
                                   AND mcc_codes IS NOT NULL AND char_length(mcc_codes) > 0))
);
CREATE UNIQUE INDEX ux_fraud_rules_name ON fraud_rules (lower(name));

CREATE TABLE transaction_rule_hits (
    id             UUID         NOT NULL,
    transaction_id UUID         NOT NULL,
    rule_id        UUID         NOT NULL,
    rule_name      VARCHAR(100) NOT NULL,
    rule_type      VARCHAR(20)  NOT NULL,
    points         INTEGER      NOT NULL,
    action         VARCHAR(10)  NOT NULL,
    detail         VARCHAR(200) NULL,
    CONSTRAINT pk_transaction_rule_hits PRIMARY KEY (id),
    CONSTRAINT fk_rule_hits_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT fk_rule_hits_rule FOREIGN KEY (rule_id) REFERENCES fraud_rules (id)
);
CREATE INDEX ix_rule_hits_transaction ON transaction_rule_hits (transaction_id);
CREATE INDEX ix_rule_hits_rule ON transaction_rule_hits (rule_id);
