-- ─────────────────────────────────────────────────────────────────
--  V2 — Add DigiLocker-flow columns to kyc_records.
--
--  The DigiLocker (MeitY) integration tracks:
--    * digilocker_state              — CSRF state token issued on /authorize
--    * digilocker_state_expires_at   — TTL so a stolen/leaked token can't be replayed
--    * aadhaar_last4                 — last 4 digits of the Aadhaar (safe to display)
--    * date_of_birth                 — DOB as DigiLocker returned it (string form)
--
--  All four are nullable — existing rows (any Digio / Mock flows) stay
--  valid. Hibernate ddl-auto=validate sees these columns as expected
--  and the boot passes.
--
--  The full 12-digit Aadhaar is NEVER stored — only the SHA-256 hash
--  (already present in aadhaar_number_hash) plus the last4 above.
--  See KycServiceImpl#completeDigilockerCallback for the hashing point.
-- ─────────────────────────────────────────────────────────────────

ALTER TABLE kyc_records ADD digilocker_state VARCHAR2(100);
ALTER TABLE kyc_records ADD digilocker_state_expires_at TIMESTAMP;
ALTER TABLE kyc_records ADD aadhaar_last4 VARCHAR2(4);
ALTER TABLE kyc_records ADD date_of_birth VARCHAR2(30);

-- Speed up the /digilocker/callback lookup which keys off the state
-- token (we already index kyc_reference_id, but state is distinct from
-- the reference id in some legacy edge cases — keep them on separate
-- indexes for clarity).
CREATE INDEX idx_kyc_records_digilocker_state ON kyc_records (digilocker_state);
