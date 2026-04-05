package org.example.hrmsystem.security;

import java.util.Collection;
import java.util.List;
import org.example.hrmsystem.model.UserAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AppUserDetails implements UserDetails {

    private final UserAccount userAccount;

    public AppUserDetails(UserAccount userAccount) {
        this.userAccount = userAccount;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + userAccount.getRole().name()));
    }

    @Override
    public String getPassword() {
        return userAccount.getPassword();
    }

    @Override
    public String getUsername() {
        return userAccount.getUsername();
    }

    @Override
    public boolean isEnabled() {
        return userAccount.isActive();
    }

    public Long getUserId() {
        return userAccount.getId();
    }

    public Long getEmployeeId() {
        return userAccount.getEmployeeId();
    }

    public String getRole() {
        return userAccount.getRole().name();
    }
}
