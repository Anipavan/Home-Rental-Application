package com.spa.home_rental_application.auth_service.filter;

import com.spa.home_rental_application.auth_service.Utils.CustomuserdetailsService;
import com.spa.home_rental_application.auth_service.Utils.JWTUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;
@Component
public class JWTAuthentationFilter extends OncePerRequestFilter {
    @Autowired
    private JWTUtil jwtUtil;
    @Autowired
    private CustomuserdetailsService customuserdetailsService;

    private String token=null;


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals("/auth/authentateUser") || path.equals("/auth/register");
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authorization=request.getHeader("Authorization");
        if(authorization!=null && authorization.startsWith("Bearer "))
        {
                token= authorization.substring(7);
        }

        String userName=jwtUtil.extractcToken(token).getSubject();
        if(userName!=null && SecurityContextHolder.getContext().getAuthentication()==null)
        {
          UserDetails userDetails= customuserdetailsService.loadUserByUsername(userName);
          if(jwtUtil.validateToken(userName,userDetails,token)){
              UsernamePasswordAuthenticationToken authToken=new UsernamePasswordAuthenticationToken
                      (userDetails,null,userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
              SecurityContextHolder.getContext().setAuthentication(authToken);
          }

        }
        filterChain.doFilter(request,response);
    }
}
