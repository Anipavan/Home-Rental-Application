package com.spa.home_rental_application.auth_service.auth_service.Repository;

import com.spa.home_rental_application.auth_service.auth_service.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface userrepo extends JpaRepository<User,Long> {
    User findByUserName(String userName);
}
