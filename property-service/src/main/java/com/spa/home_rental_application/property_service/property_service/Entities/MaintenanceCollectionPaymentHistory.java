package com.spa.home_rental_application.property_service.property_service.Entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Append-only link between a {@link MaintenanceCollection} and every
 * {@code Payment} ever minted for it.
 *
 * <p>Motivating scenario: a collection row goes through multiple
 * payment cycles when the maintainer edits {@code amountDue} after
 * a first payment lands.
 *
 * <ol>
 *   <li>Maintainer sets amountDue = ₹4000. Tenant pays via bridge
 *       → Payment₁ minted, collection.paymentId = P₁, history row
 *       {collection, P₁} inserted.</li>
 *   <li>Maintainer edits amountDue = ₹6000. Status recomputes to
 *       DUE (balance ₹2000).</li>
 *   <li>Tenant pays the ₹2000 delta → bridge mints Payment₂,
 *       collection.paymentId = P₂ (overwriting P₁), history row
 *       {collection, P₂} appended.</li>
 * </ol>
 *
 * <p>Without this table, Payment₁ becomes orphaned from the
 * collection's perspective — its uploaded proof survives on the
 * Payment itself but nothing surfaces it on the maintainer's Flat
 * charges view. With it, the enrichment loop walks every
 * historically-linked Payment and shows every proof in the
 * lightbox gallery.
 *
 * <p>The composite unique constraint {@code uq_mcph_pair} makes
 * a re-attempt during an idempotent bridge call a safe no-op —
 * saving the same (collection, payment) pair twice throws
 * {@link org.springframework.dao.DataIntegrityViolationException}
 * that the caller absorbs.
 */
@Entity
@Table(name = "maintenance_collection_payment_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_mcph_pair",
                columnNames = { "collection_id", "payment_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceCollectionPaymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "collection_id", nullable = false, length = 36)
    private String collectionId;

    @Column(name = "payment_id", nullable = false, length = 36)
    private String paymentId;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    /** Auth-user-id of whoever triggered the bridge call. Nullable
     *  when the link is written by a system path (e.g. the V18
     *  backfill script) that has no user context. */
    @Column(name = "linked_by", length = 64)
    private String linkedBy;
}
