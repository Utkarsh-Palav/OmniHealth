package com.omnihealth.common.security;

import com.omnihealth.common.enums.UserStatus;
import com.omnihealth.platform.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
public class PlatformUserPrincipal implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String fullName;
    private final UserStatus userStatus;
    private final Collection<? extends GrantedAuthority> authorities;

    public PlatformUserPrincipal(
            UUID userId,
            String email,
            String fullName,
            UserStatus userStatus,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.userStatus = userStatus;
        this.authorities = authorities != null ? authorities : Collections.emptyList();
    }

    public static PlatformUserPrincipal fromUser(User user) {
        String fullName = (user.getFirstName() + " " + (user.getLastName() != null ? user.getLastName() : "")).trim();
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

        return new PlatformUserPrincipal(
                user.getId(),
                user.getEmail(),
                fullName,
                user.getUserStatus(),
                authorities
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return userStatus != UserStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return userStatus == UserStatus.ACTIVE;
    }
}
