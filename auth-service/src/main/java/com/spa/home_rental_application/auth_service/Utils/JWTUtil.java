package com.spa.home_rental_application.auth_service.Utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JWTUtil {
    private final long timeRange=1000*60*60;
    private String secreet="U3VwZXJTZWNyZXRLZXlGb3JKV1RUb2tlbkdlbmVyYXRpb24xMjM0NTY3ODkwIQ==";
    SecretKey key= Keys.hmacShaKeyFor(secreet.getBytes());
    public String generatetocken(String userName)
    {
       return  Jwts.builder()
                .subject(userName)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()+timeRange))
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }

       public Claims extractcToken(String token)
       {
           return Jwts.parser()
                   .setSigningKey(key)
                   .build()
                   .parseClaimsJws(token)
                   .getBody();
       }

    public boolean validateToken(String userName, UserDetails userDetails, String token) {

        return userName.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }
    private boolean isTokenExpired(String token) {
        return extractcToken(token).getExpiration().before(new Date());
    }
}
