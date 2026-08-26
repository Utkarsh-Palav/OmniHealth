package com.omnihealth.common.security;

import com.omnihealth.common.enums.UserStatus;
import com.omnihealth.platform.auth.entity.PlatformSession;
import com.omnihealth.platform.auth.service.SessionService;
import com.omnihealth.platform.user.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final SessionService sessionService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String token = sessionService.extractTokenFromRequest(request);

        if (token != null && !token.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<PlatformSession> sessionOpt = sessionService.validateSession(token);

            if (sessionOpt.isPresent()) {
                PlatformSession session = sessionOpt.get();
                User user = session.getUser();

                if (user != null && user.getUserStatus() == UserStatus.ACTIVE && user.getDeletedAt() == null) {
                    PlatformUserPrincipal principal = PlatformUserPrincipal.fromUser(user);

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities()
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.trace("Authenticated user id={} via session id={}", user.getId(), session.getId());
                } else {
                    log.debug("Session validation passed but user id={} is inactive or deleted", user != null ? user.getId() : null);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
