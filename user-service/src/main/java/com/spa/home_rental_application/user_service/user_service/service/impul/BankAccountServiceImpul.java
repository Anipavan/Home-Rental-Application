package com.spa.home_rental_application.user_service.user_service.service.impul;

import com.spa.home_rental_application.KafkaEvents.Producers.Events.AuditEventPublisher;
import com.spa.home_rental_application.user_service.user_service.DTO.Request.BankAccountRequestDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.BankAccountPayoutDto;
import com.spa.home_rental_application.user_service.user_service.DTO.Response.BankAccountResponseDto;
import com.spa.home_rental_application.user_service.user_service.Entities.BankAccount;
import com.spa.home_rental_application.user_service.user_service.repositry.BankAccountRepo;
import com.spa.home_rental_application.user_service.user_service.service.BankAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class BankAccountServiceImpul implements BankAccountService {

    private final BankAccountRepo repo;
    private final AuditEventPublisher audit;

    public BankAccountServiceImpul(BankAccountRepo repo, AuditEventPublisher audit) {
        this.repo = repo;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BankAccountResponseDto> getByUserId(String userId) {
        return repo.findByUserId(userId).map(this::toDto);
    }

    @Override
    @Transactional
    public BankAccountResponseDto save(String userId, BankAccountRequestDto body) {
        BankAccount entity = repo.findByUserId(userId).orElseGet(() ->
                BankAccount.builder().userId(userId).build()
        );
        entity.setAccountHolderName(body.accountHolderName().trim());
        entity.setBankName(body.bankName().trim());
        entity.setAccountNumber(body.accountNumber().trim());
        entity.setIfscCode(body.ifscCode().trim().toUpperCase());
        entity.setBranch(blankToNull(body.branch()));
        // Default account type to SAVINGS when the client sends nothing;
        // the FE form leaves it optional so we normalise here.
        entity.setAccountType(blankToNull(body.accountType()) == null
                ? "SAVINGS"
                : body.accountType().trim().toUpperCase());
        entity.setUpiId(blankToNull(body.upiId()));
        boolean isNew = entity.getId() == null;
        BankAccount saved = repo.save(entity);
        log.info("Saved bank account for userId={} (id={})", userId, saved.getId());
        // P1-12: audit-channel emission. We deliberately do NOT log
        // any part of the account number on this row — only that
        // the user added or updated a saved account. The masked
        // form is recoverable from the entity if a forensic
        // investigation needs it.
        audit.publishSuccess(
                isNew ? "bank-account.added" : "bank-account.updated",
                userId, userId, saved.getId(),
                Map.of("bankName", saved.getBankName(),
                        "ifsc", saved.getIfscCode()));
        return toDto(saved);
    }

    @Override
    @Transactional
    public void delete(String userId) {
        repo.findByUserId(userId).ifPresent(b -> {
            repo.delete(b);
            log.info("Deleted bank account for userId={} (id={})", userId, b.getId());
            audit.publishSuccess("bank-account.removed",
                    userId, userId, b.getId(), Map.of());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BankAccountPayoutDto> getPayoutByUserId(String userId) {
        return repo.findByUserId(userId).map(this::toPayoutDto);
    }

    /* -------------------- helpers -------------------- */

    /**
     * Payable subset — the same {@code mask()} call used by the
     * full DTO, just stripped of everything a payer doesn't need.
     */
    private BankAccountPayoutDto toPayoutDto(BankAccount b) {
        return new BankAccountPayoutDto(
                b.getAccountHolderName(),
                b.getBankName(),
                mask(b.getAccountNumber()),
                b.getIfscCode(),
                b.getBranch(),
                b.getAccountType(),
                b.getUpiId()
        );
    }

    private BankAccountResponseDto toDto(BankAccount b) {
        return new BankAccountResponseDto(
                b.getId(), b.getUserId(),
                b.getAccountHolderName(),
                b.getBankName(),
                mask(b.getAccountNumber()),
                b.getIfscCode(),
                b.getBranch(),
                b.getAccountType(),
                b.getUpiId(),
                b.getCreatedAt(), b.getUpdatedAt()
        );
    }

    /**
     * Render the account number as {@code XXXX XXXX 1234} — last 4
     * digits visible, everything before substituted with {@code X}.
     * Falls back to all-X when the value is shorter than 4 chars
     * (defensive — shouldn't happen given the @Pattern constraint
     * on the request DTO, but cheaper than throwing).
     */
    static String mask(String full) {
        if (full == null) return null;
        if (full.length() <= 4) return "XXXX";
        String tail = full.substring(full.length() - 4);
        String head = "X".repeat(full.length() - 4);
        // Group the head into spaces-of-4 for readability:
        //   "XXXXXXXX1234" → "XXXX XXXX 1234"
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < head.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append(' ');
            sb.append(head.charAt(i));
        }
        sb.append(' ').append(tail);
        return sb.toString();
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
