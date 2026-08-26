package com.omnihealth.tenant.context;

import com.omnihealth.common.security.PlatformUserPrincipal;
import com.omnihealth.platform.organization.repository.PlatformOrganizationMembershipRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Binds the authenticated user's primary organization id to {@link TenantContext}
 * for the duration of the request, so tenant-scoped persistence routes to the
 * correct physical database.
 *
 * <p>Runs after {@code SessionAuthenticationFilter} (which populates the
 * {@link PlatformUserPrincipal}). The organization id is fetched via a scalar
 * projection query — never by dereferencing a LAZY association — because
 * Security filters run outside the OSIV boundary.</p>
 *
 * <p>The context is always cleared in a {@code finally} block to prevent
 * ThreadLocal leakage across pooled request threads.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    private final PlatformOrganizationMembershipRepository membershipRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof PlatformUserPrincipal principal) {
                membershipRepository.findPrimaryOrganizationIdByUserId(principal.getUserId())
                        .ifPresent(organizationId -> TenantContext.setTenantId(organizationId.toString()));
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
