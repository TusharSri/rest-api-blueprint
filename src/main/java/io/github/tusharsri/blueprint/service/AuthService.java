package io.github.tusharsri.blueprint.service;

import io.github.tusharsri.blueprint.dto.TokenResponse;
import io.github.tusharsri.blueprint.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserDetailsService users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserDetailsService users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public TokenResponse login(String username, String password) {
        UserDetails user;
        try {
            user = users.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            throw new BadCredentialsException("Invalid username or password"); // same message: no user enumeration
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return jwtService.issue(user);
    }
}
