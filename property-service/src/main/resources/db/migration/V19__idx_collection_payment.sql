-- V19: hot-path index on maintenance_collection.payment_id
--
-- Every payment.completed Kafka event triggers
-- MaintenanceCollectionRepository.findByPaymentId — including rent
-- payments where no collection row ever links to that paymentId.
-- Without this index, that lookup was a full-scan of the collections
-- table on EVERY payment completion, growing linearly with total
-- collections ever created.
--
-- Wrapped in PL/SQL exception block so re-runs against an existing
-- index (ORA-00955: name is already used by an existing object) are
-- no-ops. Same pattern the other V*__*.sql files use.
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_collection_payment ON maintenance_collection (payment_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN NULL;
        ELSE RAISE;
        END IF;
END;
/
