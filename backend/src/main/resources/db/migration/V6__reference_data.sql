INSERT INTO fraud_rules (id, name, description, rule_type, enabled, threshold_value, window_seconds, mcc_codes,
                         score_points, action, created_at, updated_at, version) VALUES
 ('00000000-0000-7000-8000-000000000001', 'High value payment',
  'Flags payments above 50,000.00 in minor units', 'MAX_AMOUNT', TRUE, 5000000, NULL, NULL, 40, 'FLAG', now(), now(), 0),
 ('00000000-0000-7000-8000-000000000002', 'Rapid-fire attempts',
  'Declines when 5 or more attempts already happened on the card in the last 60 seconds', 'VELOCITY', TRUE, 5, 60, NULL, 60, 'DECLINE', now(), now(), 0),
 ('00000000-0000-7000-8000-000000000003', 'Restricted merchant categories',
  'Declines betting merchants', 'BLOCKED_MCC', TRUE, NULL, NULL, '7995', 100, 'DECLINE', now(), now(), 0);
