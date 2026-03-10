package com.spa.home_rental_application.user_service.user_service.repositry;

import com.spa.home_rental_application.user_service.user_service.Entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRepo extends JpaRepository<User,String> {
   List< User> findByEmail(String email);
    List <User >findByPhone(String phone);
    List <User >findByFirstName(String firstName);
}
