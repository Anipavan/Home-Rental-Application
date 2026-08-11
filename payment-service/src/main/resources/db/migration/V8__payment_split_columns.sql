-- V8: payments.platform_fee + payments.owner_vendor_id
--
-- Two columns added to support Cashfree Easy Split at payment-initiate
-- time. Populated by PaymentServiceImpl.initiate() when the active
-- gateway is `cashfree` AND the owner has an ACTIVE Cashfree vendor.
--
--   platform_fee     — the commission the platform kept from the split.
--                       Zero when the owner is on a 0 % rule OR when the
--                       payment flowed direct-UPI (i.e. no split at all).
--                       Populated for BOTH split + non-split rows so admin
--                       reporting can trust it uniformly.
--   owner_vendor_id  — Cashfree's vendor_id at the moment we split.
--                       Historical — an owner can change bank details and
--                       get a new vendor_id, but the payment row keeps
--                       the id it actually paid.

ALTER TABLE payments ADD platform_fee NUMBER(10, 2) DEFAULT 0;
ALTER TABLE payments ADD owner_vendor_id VARCHAR2(64);
