-- V7: cashfree_vendors
--
-- One row per owner who has been (or is being) registered as a
-- Cashfree Easy Split vendor. Populated by the Kafka listener that
-- fires when BOTH the user's bank account is saved AND their KYC is
-- verified — see CashfreeVendorService.tryRegisterIfReady.
--
-- The row tracks Cashfree's async state machine:
--   PENDING_KYC       — bank saved but KYC not verified yet
--   PENDING_BANK      — KYC done but bank not saved yet
--   REGISTERING       — both prereqs met; API call in flight
--   IN_BANK_VALIDATION — Cashfree accepted the request, penny-drop pending
--   ACTIVE            — Cashfree has activated; can receive split payouts
--   REJECTED          — Cashfree refused (bad bank, KYC mismatch, etc.)
--   FAILED            — our API call itself errored (network, 5xx, etc.)
--
-- Only ACTIVE vendors get their share in the order_splits[] array at
-- Payment.initiate time; everyone else falls through to direct-UPI.

CREATE TABLE cashfree_vendors (
    id                       VARCHAR2(36)   PRIMARY KEY,

    /* Our stable auth user id. One row per user, ever. Recreating
       a vendor after bank/KYC changes updates this row in place. */
    user_id                  VARCHAR2(64)   NOT NULL UNIQUE,

    /* Cashfree's opaque vendor id (usually equals user_id since we
       pass user_id as the vendor_id in the create call — but stored
       separately in case Cashfree ever changes the shape). Nullable
       until the create call succeeds. */
    cashfree_vendor_id       VARCHAR2(64),

    status                   VARCHAR2(30)   DEFAULT 'PENDING_KYC' NOT NULL,

    /* Last-4 of the bank account — for display in the admin table
       (never the full account number). */
    bank_account_last4       VARCHAR2(4),
    bank_ifsc                VARCHAR2(11),
    bank_account_holder      VARCHAR2(200),

    /* Free-text from Cashfree when the registration fails / gets
       rejected. Surfaced in the admin dashboard so ops knows why. */
    failure_reason           VARCHAR2(2000),

    /* Attempt counter — increments each time tryRegisterIfReady runs.
       Caps retry storms if Cashfree is flaky. */
    attempt_count            NUMBER(3)      DEFAULT 0 NOT NULL,
    last_attempted_at        TIMESTAMP,

    /* When Cashfree transitioned this to ACTIVE. Null while pending. */
    activated_at             TIMESTAMP,

    created_at               TIMESTAMP      NOT NULL,
    updated_at               TIMESTAMP      NOT NULL
);

CREATE INDEX idx_cf_vendors_status ON cashfree_vendors (status);
CREATE INDEX idx_cf_vendors_cf_id  ON cashfree_vendors (cashfree_vendor_id);
