package com.ep.databuilder.security;

import com.ep.databuilder.user.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    @Value("${ep.jwt.secret}")
    private String secret;

    @Value("${ep.jwt.expire-hours}")
    private int expireHours;

    public String generate(UserEntity user) {
        Date expire = new Date(System.currentTimeMillis() + expireHours * 3600_000L);
        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("uid", user.getId())
                .claim("name", user.getDisplayName())
                .claim("role", user.getRole())
                .setExpiration(expire)
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    /** 解析失败/过期抛 JwtException，由调用方处理 */
    public LoginUser parse(String token) {
        Claims claims = Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
        return new LoginUser(
                ((Number) claims.get("uid")).longValue(),
                claims.getSubject(),
                (String) claims.get("name"),
                (String) claims.get("role"));
    }
}
