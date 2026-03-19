


package com.spa.home_rental_application.auth_service.Service.Impul;


import com.spa.home_rental_application.auth_service.Entity.UserDetails;
import com.spa.home_rental_application.auth_service.Repository.UserRepository;
import com.spa.home_rental_application.auth_service.Service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class UserserviceImpul implements UserService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public UserDetails registerUser(UserDetails userRequest) {

        UserDetails newUser=new UserDetails();

        newUser.setUserName(userRequest.getUsername());
        newUser.setUserPassword(passwordEncoder.encode(userRequest.getUserPassword()));
        newUser.setUserRole(userRequest.getUserRole());
        newUser.setRecordCreatedDate(Instant.now());
        newUser.setRecodeUpdatedDate(Instant.now());
        return  userRepository.save(newUser);

    }
}
