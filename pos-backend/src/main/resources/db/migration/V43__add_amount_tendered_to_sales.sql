-- Record how much the customer actually handed over, so receipts (and reprints
-- after a payment correction) can show the real tender + change given back.
--
-- cash_tendered (V36) stores only the NET cash that stays in the drawer — for a
-- CASH sale that is net_amount, with the change already deducted. That figure is
-- correct for drawer-variance math but loses the gross the customer paid, so the
-- receipt's "Cash / Change" lines could not be reconstructed once the checkout
-- screen was gone. amount_tendered preserves that gross.
--
-- Nullable: CARD/ONLINE/CREDIT sales take no cash, and legacy rows predate this
-- column. The response layer falls back to net_amount when it is null.

ALTER TABLE sales
    ADD COLUMN IF NOT EXISTS amount_tendered NUMERIC(15, 2);

-- Backfill: historical CASH sales recorded an exact tender (no change), so the
-- gross equals net_amount. Leave non-cash rows null.
UPDATE sales
SET amount_tendered = net_amount
WHERE payment_method = 'CASH'
  AND amount_tendered IS NULL;
