package com.spa.home_rental_application.auth_service.auth_service.Service;

import com.spa.home_rental_application.auth_service.auth_service.DTO.UserMapper;
import com.spa.home_rental_application.auth_service.auth_service.DTO.UserRequestDTO;
import com.spa.home_rental_application.auth_service.auth_service.DTO.UserResponseDTO;
import com.spa.home_rental_application.auth_service.auth_service.Entity.User;
import com.spa.home_rental_application.auth_service.auth_service.Repository.userrepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class userService {
    @Autowired
    userrepo repo;
    @Autowired
    PasswordEncoder encoder;
    public UserResponseDTO registerUser(UserRequestDTO userRequestDTO)
    {
        UserMapper mapper=new UserMapper(encoder);
        User user=mapper.toEntity(userRequestDTO);


        return mapper.toResponseDTO(repo.save(user));
    }
}
