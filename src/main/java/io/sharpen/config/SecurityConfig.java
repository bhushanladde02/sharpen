package io.sharpen.config;

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
        return email -> people.findByEmailIgnoreCase(email)
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
                        .requestMatchers("/api/v1/health").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/register", "/p/**", "/css/**", "/js/**", "/img/**",
                                "/error", "/h2-console/**").permitAll()
                        .requestMatchers("/candidates/**").hasRole("COMPANY")
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/dashboard", false).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/").permitAll())
                .headers(h -> h.frameOptions(f -> f.sameOrigin())) // H2 console in dev
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"));
        return http.build();
    }
}
