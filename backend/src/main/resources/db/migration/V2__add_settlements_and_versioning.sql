-- SplitEase v2 Phase 2: Expense engine (equal/exact/percentage splits),
-- optimistic locking, balances, and settlements.

-- Add split_type and optimistic-lock version to expenses.
-- (category already exists from V1.)
ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS split_type VARCHAR(20) NOT NULL DEFAULT 'EQUAL' AFTER total_amount,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0 AFTER updated_at;

-- Add optional percentage column to expense_splits for PERCENTAGE split type.
ALTER TABLE expense_splits
    ADD COLUMN IF NOT EXISTS percentage DECIMAL(5, 2) NULL AFTER owed_amount;

-- Settlements table: records money paid between two group members to settle debt.
CREATE TABLE IF NOT EXISTS settlements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    payer_id BIGINT NOT NULL,
    payee_id BIGINT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    notes VARCHAR(255),
    settled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_settlements_group FOREIGN KEY (group_id) REFERENCES expense_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_settlements_payer FOREIGN KEY (payer_id) REFERENCES users(id),
    CONSTRAINT fk_settlements_payee FOREIGN KEY (payee_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes for settlement lookups.
CREATE INDEX IF NOT EXISTS idx_settlements_group ON settlements(group_id);
CREATE INDEX IF NOT EXISTS idx_settlements_payer ON settlements(payer_id);
CREATE INDEX IF NOT EXISTS idx_settlements_payee ON settlements(payee_id);