package com.omnihealth.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnihealth.common.constants.ApiRoutes;
import com.omnihealth.common.exception.CommonErrorCode;
import com.omnihealth.common.response.ApiMeta;
import com.omnihealth.common.response.ApiResponse;
import com.omnihealth.tenant.context.TenantContextFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SessionAuthenticationFilter sessionAuthenticationFilter;
    private final TenantContextFilter tenantContextFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint()))
                .authorizeHttpRequests(auth -> auth
                        // Public User Registration
                        .requestMatchers(HttpMethod.POST, ApiRoutes.USERS, ApiRoutes.USERS + "/").permitAll()
                        .requestMatchers(ApiRoutes.USERS + "/verify-email").permitAll()

                        // Public Self-Service Signup (creates the owner account + draft org)
                        .requestMatchers(HttpMethod.POST, ApiRoutes.ONBOARDING + "/signup").permitAll()

                        // Public Auth Endpoints (login, email verification)
                        .requestMatchers(ApiRoutes.AUTH + "/login", ApiRoutes.AUTH + "/login/**").permitAll()
                        .requestMatchers(ApiRoutes.AUTH + "/verify-email", ApiRoutes.AUTH + "/verify-email/**").permitAll()

                        // Swagger / OpenAPI docs
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/error"
                        ).permitAll()

                        // Protected Endpoints
                        .anyRequest().authenticated()
                )
                .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(tenantContextFilter, SessionAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                    .success(false)
                    .code(CommonErrorCode.UNAUTHORIZED.getCode())
                    .message("Full authentication is required to access this resource.")
                    .meta(ApiMeta.builder().path(request.getRequestURI()).build())
                    .build();

            objectMapper.writeValue(response.getOutputStream(), apiResponse);
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
