-- V5: razorpay_route_accounts
--
-- One row per owner who has (or is applying to) receive split
-- payments via Razorpay Route. Owned by payment-service; keyed by
-- user_id so we don't couple to user-service's schema.
--
-- Sensitive fields (raw PAN, full bank account number) are NOT
-- stored here — we send them to Razorpay to create the Linked
-- Account, keep only last-four for display, and rely on Razorpay
-- as the system of record for the full values.

CREATE TABLE razorpay_route_accounts (
    id                          VARCHAR2(36)   PRIMARY KEY,
    user_id                     VARCHAR2(64)   NOT NULL,
    razorpay_account_id         VARCHAR2(64),
    status                      VARCHAR2(30)   DEFAULT 'NOT_STARTED' NOT NULL,

    -- Business identity
    business_type               VARCHAR2(30),
    legal_business_name         VARCHAR2(200),
    business_name               VARCHAR2(200),
    contact_name                VARCHAR2(200),
    contact_email               VARCHAR2(200),
    contact_phone               VARCHAR2(20),

    -- Identifiers stored only as last-four
    pan_last4                   VARCHAR2(4),
    gst_number                  VARCHAR2(20),

    -- Bank account (last-four + IFSC + holder name)
    bank_account_last4          VARCHAR2(4),
    bank_ifsc                   VARCHAR2(11),
    bank_account_holder_name    VARCHAR2(200),

    -- Registered address
    address_line1               VARCHAR2(200),
    address_line2               VARCHAR2(200),
    address_city                VARCHAR2(100),
    address_state               VARCHAR2(100),
    address_postal_code         VARCHAR2(10),
    address_country             VARCHAR2(2)    DEFAULT 'IN',

    -- Lifecycle timestamps + reason
    kyc_submitted_at            TIMESTAMP,
    activated_at                TIMESTAMP,
    status_reason               VARCHAR2(2000),
    last_status_updated_at      TIMESTAMP,

    created_at                  TIMESTAMP      NOT NULL,
    updated_at                  TIMESTAMP      NOT NULL,

    CONSTRAINT uq_route_acc_user UNIQUE (user_id)
);

CREATE INDEX idx_route_acc_status ON razorpay_route_accounts (status);
CREATE INDEX idx_route_acc_rzp_id ON razorpay_route_accounts (razorpay_account_id);
