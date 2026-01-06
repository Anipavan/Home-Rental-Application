package com.spa.home_rental_application.auth_service.auth_service.service;

import com.spa.home_rental_application.auth_service.auth_service.repository.UserRepository;
import lombok.NoArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@NoArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private UserRepository userRepository;
    public CustomUserDetailsService(UserRepository userRepository)
    {
        this.userRepository=userRepository;
    }
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username).orElseThrow(()->new IllegalArgumentException("User not found"));
    }
}
