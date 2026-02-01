package com.monstersinc.stock101.auth.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtUtil jwtUtil;
    private static final long ACCESS_TOKEN_EXPIRATION = 1000L * 60L * 30; // 30분
    private static final long REFRESH_TOKEN_EXPIRATION = 1000L * 60L * 60L * 24L; // 1일
    private final RedisTemplate<String, String> redisTemplate;

    public String createAccessToken(Long userId, List<String> roles) {
        Map<String, Object> claims = Map.of("userId", userId, "roles",roles,"token_type", "access");
        return jwtUtil.createJwtToken(claims, ACCESS_TOKEN_EXPIRATION);
    }

    public String createRefreshToken(Long userId) {
        Map<String, Object> claims = Map.of("userId", userId, "token_type", "refresh");
        String refreshToken = jwtUtil.createJwtToken(claims, REFRESH_TOKEN_EXPIRATION);

        redisTemplate.opsForValue().set(
                "refresh:" + userId, refreshToken, REFRESH_TOKEN_EXPIRATION, TimeUnit.MILLISECONDS);

        return refreshToken;
    }

    public String resolveToken(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    public void addBlacklist(String accessToken) {
        String key = "blacklist:" + jwtUtil.getJti(accessToken);

        redisTemplate.opsForValue().set(key, accessToken, ACCESS_TOKEN_EXPIRATION, TimeUnit.MILLISECONDS);
    }

    public void deleteRefreshToken(String accessToken) {
        String userId = jwtUtil.getUserId(accessToken);

        redisTemplate.delete("refresh:" + userId);
    }

    public boolean isUsableAccessToken(String accessToken) {
        return accessToken != null
                && jwtUtil.validateToken(accessToken)
                && !isBlacklist(accessToken)
                && isAccessToken(accessToken);
    }

    public Authentication createAuthentication(String accessToken) {
        Long userId = Long.parseLong(jwtUtil.getUserId(accessToken));
        List<String> roles = jwtUtil.getRoles(accessToken);

        List<GrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        return new UsernamePasswordAuthenticationToken(userId, null, authorities);
    }

    private boolean isAccessToken(String accessToken) {
        return jwtUtil.getTokenType(accessToken).equals("access");
    }

    private boolean isBlacklist(String accessToken) {
        String key = "blacklist:" + jwtUtil.getJti(accessToken);
        return redisTemplate.hasKey(key);
    }

    public boolean isValidRefreshToken(String refreshToken) {
        String userId = jwtUtil.getUserId(refreshToken);
        String storedRefreshToken = redisTemplate.opsForValue().get("refresh:" + userId);

        return storedRefreshToken != null && storedRefreshToken.equals(refreshToken);
    }
}
