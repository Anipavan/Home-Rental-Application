package com.spa.home_rental_application.user_service.user_service.repositry;

import com.spa.home_rental_application.user_service.user_service.Entities.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankAccountRepo extends JpaRepository<BankAccount, String> {

    /**
     * Each user gets at most one bank account on file (uk_bank_accounts_user
     * enforces this at the DB level). This method drives the
     * "upsert" service path — present → update in place; absent → insert.
     */
    Optional<BankAccount> findByUserId(String userId);

    void deleteByUserId(String userId);
}
