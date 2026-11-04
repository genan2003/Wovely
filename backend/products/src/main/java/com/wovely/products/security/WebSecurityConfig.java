package com.wovely.products.security;

import com.wovely.common.security.AuthTokenFilter;
import com.wovely.common.security.InternalApiKeyFilter;
import com.wovely.common.security.JwtPrincipal;
import com.wovely.common.security.JwtUtils;
import com.wovely.common.security.OwnershipGuard;
import com.wovely.common.security.RestAuthHandlers;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Products service access rules.
 * <ul>
 *   <li>Catalog, categories and reviews: public reads.</li>
 *   <li>Creating products: sellers/admins; managing them: owner or admin (see {@link OwnershipGuard}).</li>
 *   <li>Stock mutations used by other services: {@code /internal/**}, internal API key only.</li>
 * </ul>
 */
@Configuration
@org.springframework.context.annotation.Import(com.wovely.common.security.ClaimsSecurityConfiguration.class)
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtUtils jwtUtils, RestAuthHandlers authHandlers,
            @Value("${wovely.internal.apiKey:}") String internalApiKey) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(authHandlers).accessDeniedHandler(authHandlers))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error").permitAll()
                        // liveness/readiness for docker healthchecks; details never exposed (PLAN.md 4.6)
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // API docs (PLAN.md 4.5); disable with SPRINGDOC_API_DOCS_ENABLED=false in production
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                .requestMatchers("/internal/**").hasAuthority(JwtPrincipal.SERVICE_ROLE)
                .requestMatchers(GET, "/api/categories/**").permitAll()
                .requestMatchers("/api/categories/**").hasRole("ADMIN")
                .requestMatchers("/api/products/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/products/seller/**").hasAnyRole("SELLER", "ADMIN")
                .requestMatchers(GET, "/api/products", "/api/products/*").permitAll()
                .requestMatchers(POST, "/api/products").hasAnyRole("SELLER", "ADMIN")
                .requestMatchers(GET, "/api/reviews/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(new InternalApiKeyFilter(internalApiKey), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new AuthTokenFilter(jwtUtils), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
