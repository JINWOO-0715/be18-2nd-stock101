package com.monstersinc.stock101.config;

import com.monstersinc.stock101.auth.jwt.JwtAuthenticationFilter;
import com.monstersinc.stock101.exception.handler.CustomAuthenticationEntryPoint;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, LogbackMetrics logbackMetrics) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(customizer ->
                        customizer.configurationSource(getCorsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        // 1) 로그인 관련
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()

                        // 2) 사용자 정보 관련
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/me").authenticated()

                        // 2) 게시물, 좋아요, 댓글 등록 및 내가 작성한 게시물 조회; 로그인 필요
                        .requestMatchers(HttpMethod.POST, "/api/v1/board/posts").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/board/posts/{postId}/like").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/board/posts/{postId}/comments").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/board/me").authenticated()

                        // 3) 공시보고서 업로드; 로그인 필요
                        .requestMatchers(HttpMethod.POST, "/api/disclosure/upload").authenticated()

                        // 나머지 요청은 일단 모두 허용.
                        .anyRequest().permitAll()
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new CustomAuthenticationEntryPoint()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .logout(logout -> logout.disable());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }


    private static CorsConfigurationSource getCorsConfigurationSource() {

        return (request) -> {
            // CorsConfiguration 객체를 생성해서 CORS 설정을 한다.
            CorsConfiguration corsConfiguration = new CorsConfiguration();

            // CORS 요청에서 허용할 출처를 지정한다.
            corsConfiguration.setAllowedOriginPatterns(List.of("*"));

            // CORS 요청에서 허용할 HTTP 메소드를 지정한다.
            corsConfiguration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

            // 클라이언트가 요청 시 사용할 수 있는 헤더를 지정한다.
            corsConfiguration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Cache-Control", "X-Requested-With"));

            // 클라이언트가 응답에서 접근할 수 있는 헤더를 지정한다. (SSE 관련 헤더 포함)
            corsConfiguration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));

            // 자격 증명(쿠키, 세션) 허용 여부를 설정한다.
            corsConfiguration.setAllowCredentials(true);

            // CORS Preflight 요청을 브라우저가 캐싱하는 시간(초 단위)을 설정한다.
            corsConfiguration.setMaxAge(3600L);

            return corsConfiguration;
        };
    }

}
