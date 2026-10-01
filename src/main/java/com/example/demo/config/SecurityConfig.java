package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                // Disable CSRF for external webhook endpoints and REST APIs
                .ignoringRequestMatchers("/api/webhooks/**", "/api/rules/**")
            )
            .authorizeHttpRequests(auth -> auth
                // GitHub Webhook ingestion must be publicly accessible
                .requestMatchers("/api/webhooks/**").permitAll()
                // Static resources & health check
                .requestMatchers("/", "/css/**", "/js/**", "/error", "/api/health").permitAll()
                // All dashboard interactions require authentication
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .defaultSuccessUrl("/dashboard", true)
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            );

        return http.build();
    }
}
