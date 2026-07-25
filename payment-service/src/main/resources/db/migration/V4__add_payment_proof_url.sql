-- Tenant-uploaded payment-proof screenshot URL. Set only after the
-- tenant hits POST /payments/{id}/upload-proof from the direct-UPI
-- pay page (a PhonePe / GPay / Paytm success screen typically).
-- Nullable — a payment can still be marked PAID via other paths
-- (owner mark-received, tenant self-report without proof, Razorpay
-- webhook) so this column is a convenience aid on the maintainer's
-- dashboard, not a required field for settlement.
--
-- 500 chars matches the URL length cap we use on other file
-- reference columns across the codebase (lease deeds, GST invoices)
-- so the same path shapes work everywhere without truncation.
ALTER TABLE payments
    ADD payment_proof_url VARCHAR2(500);
