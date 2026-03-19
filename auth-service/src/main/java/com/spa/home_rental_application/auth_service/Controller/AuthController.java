package com.spa.home_rental_application.auth_service.Controller;



import com.spa.home_rental_application.auth_service.Entity.AuthUser;
import com.spa.home_rental_application.auth_service.Entity.UserDetails;
import com.spa.home_rental_application.auth_service.Service.UserService;
import com.spa.home_rental_application.auth_service.Utils.JWTUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private UserService service;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JWTUtil jwtUtil;

    @PostMapping("/register")
    public UserDetails registerUser(@RequestBody UserDetails userRequest)
    {

        return service.registerUser(userRequest);
    }

    @PostMapping("/authentateUser")
    public String generatetoken(@RequestBody AuthUser authRequest)
    {
        try {
            authenticationManager.authenticate(new
                    UsernamePasswordAuthenticationToken(authRequest.getUserName(), authRequest.getPassword()));
        }
        catch (Exception ex)
        {
            throw ex;
        }
        return jwtUtil.generatetocken(authRequest.getUserName());
    }
}
