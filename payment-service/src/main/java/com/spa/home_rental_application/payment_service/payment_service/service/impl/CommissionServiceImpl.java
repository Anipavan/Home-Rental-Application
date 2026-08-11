package com.spa.home_rental_application.payment_service.payment_service.service.impl;

import com.spa.home_rental_application.payment_service.payment_service.DTO.CommissionDtos;
import com.spa.home_rental_application.payment_service.payment_service.entities.CommissionRule;
import com.spa.home_rental_application.payment_service.payment_service.repository.CommissionRuleRepository;
import com.spa.home_rental_application.payment_service.payment_service.service.CommissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class CommissionServiceImpl implements CommissionService {

    /** Basis-points denominator — 200 bps × amount / 10000 → 2 % of amount. */
    private static final BigDecimal BPS_DENOMINATOR = BigDecimal.valueOf(10_000);

    private final CommissionRuleRepository repo;

    public CommissionServiceImpl(CommissionRuleRepository repo) {
        this.repo = repo;
    }

    /* ------------- Runtime compute (called from PaymentServiceImpl) ------------- */

    @Override
    @Transactional(readOnly = true)
    public BigDecimal computePlatformFee(String ownerId, BigDecimal totalAmount) {
        if (totalAmount == null || totalAmount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        int bps = rateBpsFor(ownerId);
        if (bps == 0) return BigDecimal.ZERO;
        return totalAmount
                .multiply(BigDecimal.valueOf(bps))
                .divide(BPS_DENOMINATOR, 2, RoundingMode.HALF_UP);
    }

    /**
     * Two-tier lookup: per-owner override wins; else the global default;
     * else 0 (safe fallback if the V6 seed somehow didn't run — the
     * platform earns nothing, but payments still flow, which is
     * strictly better than blocking rent collection on a config gap).
     */
    private int rateBpsFor(String ownerId) {
        if (ownerId != null && !ownerId.isBlank()) {
            Optional<CommissionRule> override = repo.findByOwnerId(ownerId);
            if (override.isPresent()) return override.get().getRateBps();
        }
        return repo.findGlobal().map(CommissionRule::getRateBps).orElse(0);
    }

    /* ------------- Admin CRUD ------------- */

    @Override
    @Transactional(readOnly = true)
    public CommissionDtos.RuleResponse getGlobal() {
        // Auto-seed if missing — belts and braces so a broken V6 doesn't
        // leave the admin page with nothing to edit. Should never fire
        // in normal deployments.
        CommissionRule row = repo.findGlobal().orElseGet(() -> {
            log.warn("Global commission rule missing — seeding at 0 bps on demand");
            CommissionRule seed = CommissionRule.builder()
                    .ownerId(null)
                    .rateBps(0)
                    .notes("Auto-seeded on first admin read.")
                    .build();
            return repo.save(seed);
        });
        return CommissionDtos.RuleResponse.from(row);
    }

    @Override
    @Transactional
    public CommissionDtos.RuleResponse setGlobal(CommissionDtos.UpsertRuleRequest req,
                                                  String actorAdminId) {
        CommissionRule row = repo.findGlobal().orElseGet(() -> CommissionRule.builder()
                .ownerId(null)
                .rateBps(0)
                .build());
        row.setRateBps(toBps(req.ratePercent()));
        row.setNotes(nullIfBlank(req.notes()));
        row.setUpdatedByAdminId(actorAdminId);
        CommissionRule saved = repo.save(row);
        log.info("Global commission rate set to {} bps by adminId={}",
                saved.getRateBps(), actorAdminId);
        return CommissionDtos.RuleResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommissionDtos.RuleResponse> listOverrides() {
        return repo.findAllOverrides().stream()
                .map(CommissionDtos.RuleResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public CommissionDtos.RuleResponse upsertOverride(String ownerId,
                                                      CommissionDtos.UpsertRuleRequest req,
                                                      String actorAdminId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId is required for a per-owner override");
        }
        CommissionRule row = repo.findByOwnerId(ownerId).orElseGet(() -> CommissionRule.builder()
                .ownerId(ownerId)
                .rateBps(0)
                .build());
        row.setRateBps(toBps(req.ratePercent()));
        row.setNotes(nullIfBlank(req.notes()));
        row.setUpdatedByAdminId(actorAdminId);
        CommissionRule saved = repo.save(row);
        log.info("Per-owner commission override set: ownerId={} rate={} bps by adminId={}",
                ownerId, saved.getRateBps(), actorAdminId);
        return CommissionDtos.RuleResponse.from(saved);
    }

    @Override
    @Transactional
    public void deleteOverride(String ownerId, String actorAdminId) {
        if (ownerId == null || ownerId.isBlank()) return;
        repo.findByOwnerId(ownerId).ifPresent(row -> {
            repo.delete(row);
            log.info("Per-owner commission override removed: ownerId={} by adminId={}",
                    ownerId, actorAdminId);
        });
    }

    /* ------------- Preview ------------- */

    @Override
    @Transactional(readOnly = true)
    public CommissionDtos.PreviewResponse preview(String ownerId, BigDecimal totalAmount) {
        BigDecimal amount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        int bps;
        String source;
        String resolvedOwner = null;
        if (ownerId != null && !ownerId.isBlank()) {
            Optional<CommissionRule> override = repo.findByOwnerId(ownerId);
            if (override.isPresent()) {
                bps = override.get().getRateBps();
                source = "owner";
                resolvedOwner = ownerId;
            } else {
                bps = repo.findGlobal().map(CommissionRule::getRateBps).orElse(0);
                source = "global";
            }
        } else {
            bps = repo.findGlobal().map(CommissionRule::getRateBps).orElse(0);
            source = "global";
        }
        BigDecimal fee = bps == 0
                ? BigDecimal.ZERO
                : amount.multiply(BigDecimal.valueOf(bps))
                        .divide(BPS_DENOMINATOR, 2, RoundingMode.HALF_UP);
        BigDecimal ownerAmount = amount.subtract(fee).max(BigDecimal.ZERO);
        BigDecimal pct = BigDecimal.valueOf(bps).movePointLeft(2);
        return new CommissionDtos.PreviewResponse(
                amount.setScale(2, RoundingMode.HALF_UP),
                fee.setScale(2, RoundingMode.HALF_UP),
                ownerAmount.setScale(2, RoundingMode.HALF_UP),
                bps,
                pct,
                source,
                resolvedOwner
        );
    }

    /* ------------- helpers ------------- */

    private static int toBps(BigDecimal pct) {
        if (pct == null) return 0;
        int bps = pct.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
        if (bps < 0 || bps > 10_000) {
            throw new IllegalArgumentException(
                    "ratePercent out of range after conversion (bps=" + bps + ")");
        }
        return bps;
    }

    private static String nullIfBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
