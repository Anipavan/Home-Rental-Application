package com.spa.home_rental_application.user_service.user_service.repositry;

import com.spa.home_rental_application.user_service.user_service.Entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepo extends JpaRepository<User,String> {
    User findByEmail(String email);
}
