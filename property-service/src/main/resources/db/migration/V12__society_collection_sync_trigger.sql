-- Self-healing sync rule: when a Payment row goes PAID, automatically
-- flip every maintenance_collection row whose payment_id matches it to
-- PAID with paid_via=RAZORPAY and paid_on=today.
--
-- WHY a DB trigger and not the Kafka listener:
--   The existing SocietyChargePaymentListener consumes PaymentCompletedEvent
--   to do the same flip. When it works, great. But Kafka has been a recurring
--   weak link in this stack — hostname resolution races, cluster-id drift,
--   broker outages. Every time Kafka coughs, payments succeed in payment-service
--   but property-service's collection rows stay DUE, so the resident sees "you
--   still owe ₹X" even though they just paid. That's the worst possible UX
--   for a payment app.
--
--   A trigger sits at the data layer where the invariant actually lives:
--     "If Payment.status = PAID and a MAINTENANCE_COLLECTION.payment_id
--      equals that Payment's id, then the collection row IS paid."
--   No matter who or what updates payments.status — Kafka listener, manual
--   SQL repair, a future direct-write code path — this stays in sync. Pure
--   defence in depth.
--
-- WHY idempotent (only flips DUE / OVERDUE → PAID):
--   A trigger that always runs can fire many times for the same row if the
--   Payment status is re-touched. Guarding on the current collection status
--   means it only does work on rows that need flipping. Re-firing is a no-op.
--
-- WHY no rent impact:
--   Rent payments don't have rows in MAINTENANCE_COLLECTION (only society
--   charges do, via the SocietyServiceImpl.initiateSocietyChargePayment path).
--   So for a rent Payment going PAID, the WHERE clause matches zero rows
--   and the trigger silently does nothing. No-op, no overhead worth caring
--   about for the volume we have.
CREATE OR REPLACE TRIGGER sync_collection_paid_from_payment
AFTER UPDATE OF status ON payments
FOR EACH ROW
WHEN (NEW.status = 'PAID' AND (OLD.status IS NULL OR OLD.status != 'PAID'))
BEGIN
    UPDATE maintenance_collection
       SET status      = 'PAID',
           amount_paid = amount_due,
           paid_via    = COALESCE(paid_via, 'RAZORPAY'),
           paid_on     = COALESCE(paid_on, TRUNC(SYSDATE)),
           updated_at  = SYSTIMESTAMP
     WHERE payment_id = :NEW.id
       AND status IN ('DUE', 'OVERDUE');
END;
/

-- One-time backfill for any historical drift: every Payment row that's
-- already PAID where the linked collection rows are still DUE / OVERDUE.
-- Catches the rows that today's Kafka outage missed.
UPDATE maintenance_collection mc
   SET status      = 'PAID',
       amount_paid = mc.amount_due,
       paid_via    = COALESCE(mc.paid_via, 'RAZORPAY'),
       paid_on     = COALESCE(mc.paid_on, TRUNC(SYSDATE)),
       updated_at  = SYSTIMESTAMP
 WHERE mc.status IN ('DUE', 'OVERDUE')
   AND mc.payment_id IS NOT NULL
   AND EXISTS (
       SELECT 1 FROM payments p
        WHERE p.id = mc.payment_id
          AND p.status = 'PAID'
   );
