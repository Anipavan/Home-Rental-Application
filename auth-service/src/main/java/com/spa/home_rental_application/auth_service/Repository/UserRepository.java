package com.spa.home_rental_application.auth_service.Repository;


import com.spa.home_rental_application.auth_service.Entity.UserDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository  extends JpaRepository<UserDetails,Long> {
    UserDetails findByUserName(String username);
}
