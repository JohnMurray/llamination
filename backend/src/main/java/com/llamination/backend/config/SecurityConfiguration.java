package com.llamination.backend.config;

import com.llamination.backend.auth.UserAuthenticationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/** Defines cookie-session security while leaving credential verification in the auth domain. */
@Configuration
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    AuthenticationManager authenticationManager(UserAuthenticationService authenticationService) {
        return authentication -> authenticationService
                .authenticate(authentication.getName(), String.valueOf(authentication.getCredentials()))
                .<org.springframework.security.core.Authentication>map(identity ->
                        new UsernamePasswordAuthenticationToken(
                                identity, null, AuthorityUtils.NO_AUTHORITIES))
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // The browser API already relies on same-site session cookies; token-based CSRF is a later hardening task.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/**", "/", "/index.html", "/assets/**", "/error").permitAll()
                        .requestMatchers("/api/**", "/ws/**").authenticated()
                        .anyRequest().permitAll())
                .build();
    }
}
