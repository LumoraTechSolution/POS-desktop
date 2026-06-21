-- P1 1.3: Track the cash portion of every sale so variance is correct for SPLIT payments.
-- Previously the drawer query only summed CASH-method sales, silently short-counting
-- any mixed-tender (cash + card) transaction. Adding cash_tendered lets the query
-- sum actual cash received regardless of payment_method.

ALTER TABLE sales
    ADD COLUMN IF NOT EXISTS cash_tendered NUMERIC(15, 2) NOT NULL DEFAULT 0;

-- Backfill: historical CASH sales tendered exactly net_amount.
UPDATE sales
SET cash_tendered = net_amount
WHERE payment_method = 'CASH'
  AND cash_tendered = 0;
