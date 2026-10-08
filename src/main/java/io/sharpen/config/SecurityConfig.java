package io.sharpen.config;

import io.sharpen.auth.SocialLoginHandlers;
import io.sharpen.auth.SocialRegistrations;
import io.sharpen.auth.SocialUserServices;
import io.sharpen.repo.PersonRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Two chains: a stateless API-key chain for {@code /api/**} (extension, importers, CI) and a session/form chain
 * for the browser UI. Public profile pages are readable without an account so a recruiter can follow a link.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PersonRepository people) {
        // An account made through Google or GitHub has no password: to the password form it does not exist, so
        // the form says "did not match" rather than revealing which accounts sign in some other way.
        return email -> people.findByEmailIgnoreCase(email)
                .filter(p -> p.hasPassword())
                .map(p -> User.withUsername(p.getEmail())
                        .password(p.getPasswordHash())
                        .roles(p.isCompany() ? "COMPANY" : "INDIVIDUAL")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http, ApiKeyAuthFilter apiKeyFilter) throws Exception {
        http.securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(apiKeyFilter, BasicAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health", "/api/v1/public/**").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webChain(HttpSecurity http, SocialRegistrations social, SocialUserServices socialUsers,
                                        SocialLoginHandlers socialHandlers) throws Exception {
        // "Continue with Google / GitHub", only for the providers configured on this deployment. Spring handles
        // the redirect, state and nonce checks and the code exchange; SocialUserServices maps the result to a person.
        if (!social.isEmpty()) {
            http.oauth2Login(o -> o
                    .loginPage("/login")
                    .clientRegistrationRepository(social)
                    .authorizedClientRepository(new io.sharpen.auth.DiscardingAuthorizedClientRepository())
                    .userInfoEndpoint(u -> u.oidcUserService(socialUsers.oidc()).userService(socialUsers.oauth2()))
                    .successHandler(socialHandlers.success())
                    .failureHandler(socialHandlers.failure()));
        }
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/register", "/feedback", "/p", "/p/**", "/profiles", "/insights", "/privacy", "/css/**", "/js/**", "/img/**", "/fonts/**", "/favicon.ico", "/robots.txt", "/sitemap.xml",
                                "/error", "/h2-console/**").permitAll()
                        .requestMatchers("/candidates/**").hasRole("COMPANY")
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/dashboard", false).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/").permitAll())
                .headers(h -> h
                        .frameOptions(f -> f.sameOrigin())   // H2 console in dev
                        // Outbound links carry only the origin (never a profile or report URL) and only to https;
                        // a Sharpen page never asks the browser for camera, microphone, location or payment.
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=(), payment=(), usb=()")))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"));
        return http.build();
    }
}
