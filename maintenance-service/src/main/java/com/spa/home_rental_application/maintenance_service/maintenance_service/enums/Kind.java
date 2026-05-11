package com.spa.home_rental_application.maintenance_service.maintenance_service.enums;

/**
 * Discriminator on every ticket stored in the {@code maintenance_requests}
 * collection. The lifecycle (status machine, comments, history,
 * attachments, notification fan-out) is identical for both kinds —
 * the discriminator just changes:
 *
 * <ul>
 *   <li>which category enum is valid ({@link Category} for MAINTENANCE,
 *       {@link ComplaintCategory} for COMPLAINT);</li>
 *   <li>which collection of UI pages renders it (tenant /app/maintenance
 *       vs /app/complaints, owner /owner/maintenance vs /owner/complaints);</li>
 *   <li>the wording on notifications (a tenant raising a complaint vs.
 *       a maintenance request gets different toast copy).</li>
 * </ul>
 *
 * <p>Legacy rows (created before this discriminator existed) default to
 * MAINTENANCE through the entity's {@code @Builder.Default}.
 */
public enum Kind {
    MAINTENANCE,
    COMPLAINT
}
