-- Many-to-many link between maintenance_collections and Payments,
-- kept as an append-only history so a collection row that goes
-- through multiple payment cycles preserves the whole chain.
--
-- Motivating scenario:
--   1. Maintainer sets amountDue = 4000. Tenant pays via bridge →
--      Payment₁ minted, collection.payment_id = P₁.
--   2. Maintainer edits amountDue = 6000 (actual amount). Status
--      recomputes to DUE (balance 2000).
--   3. Tenant pays the 2000 delta → bridge mints Payment₂,
--      collection.payment_id = P₂ (overwriting P₁).
--
-- Without this table, Payment₁ becomes orphaned from the collection's
-- perspective — its proof screenshot survives on the Payment itself
-- but nothing in the maintainer's Flat charges view surfaces it any
-- more. With this table, every bridge-mint inserts a row here in
-- addition to stamping collection.payment_id, so the enrichment loop
-- can walk every historically-linked Payment and show all their
-- proofs in the maintainer lightbox.
--
-- Unique (collection_id, payment_id) guarantees a re-attempt during
-- an idempotent bridge call doesn't duplicate the link.
CREATE TABLE maintenance_collection_payment_history (
    id            VARCHAR2(36) PRIMARY KEY,
    collection_id VARCHAR2(36) NOT NULL,
    payment_id    VARCHAR2(36) NOT NULL,
    linked_at     TIMESTAMP    NOT NULL,
    linked_by     VARCHAR2(64),
    CONSTRAINT fk_mcph_collection
        FOREIGN KEY (collection_id)
        REFERENCES maintenance_collections(id)
        ON DELETE CASCADE,
    CONSTRAINT uq_mcph_pair UNIQUE (collection_id, payment_id)
);

CREATE INDEX idx_mcph_collection ON maintenance_collection_payment_history (collection_id);
CREATE INDEX idx_mcph_payment    ON maintenance_collection_payment_history (payment_id);

-- Backfill: seed history rows for any existing collection that
-- already has a payment_id stamped. Ensures the enrichment
-- immediately picks up historical proofs on the first deploy
-- rather than only for post-deploy payment cycles.
INSERT INTO maintenance_collection_payment_history
    (id, collection_id, payment_id, linked_at, linked_by)
SELECT
    LOWER(RAWTOHEX(SYS_GUID())),
    id,
    payment_id,
    COALESCE(updated_at, SYSTIMESTAMP),
    'backfill-v18'
FROM maintenance_collections
WHERE payment_id IS NOT NULL;
