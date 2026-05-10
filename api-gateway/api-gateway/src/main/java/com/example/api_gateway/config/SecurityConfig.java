package com.example.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Flux;

import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;

@Configuration
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("scope");
        grantedAuthoritiesConverter.setAuthorityPrefix("");

        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt ->
                Flux.fromIterable(grantedAuthoritiesConverter.convert(jwt))
        );
        return converter;
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA384");
        return NimbusReactiveJwtDecoder
                .withSecretKey(secretKeySpec)
                .macAlgorithm(MacAlgorithm.HS384)
                .build();
    }

    @Bean
    public ServerAuthenticationEntryPoint authenticationEntryPoint() {
        return new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED);
    }

        // Public endpoints — no JWT filter, no auth required
    @Bean
    @Order(1)
    public SecurityWebFilterChain publicSecurityWebFilterChain(ServerHttpSecurity http, CorsConfigurationSource cors) {
        http
                .securityMatcher(new OrServerWebExchangeMatcher(
                        new PathPatternParserServerWebExchangeMatcher("/api/auth/login"),
                        new PathPatternParserServerWebExchangeMatcher("/api/auth/register"),
                        new PathPatternParserServerWebExchangeMatcher("/api/users/register"),
                        new PathPatternParserServerWebExchangeMatcher("/api/users/upload-profile/**"),
                        // WebSocket endpoints
                        new PathPatternParserServerWebExchangeMatcher("/ws/**"),
                        new PathPatternParserServerWebExchangeMatcher("/api/notifications/trigger-daily"),
                        new PathPatternParserServerWebExchangeMatcher("/api/notifications/trigger-deadline-check"),
                        // Eureka
                        new PathPatternParserServerWebExchangeMatcher("/eureka/**"),
                        new PathPatternParserServerWebExchangeMatcher("/internal/**"),
                        new PathPatternParserServerWebExchangeMatcher("/actuator/**")
                ))
                .cors(cors1 -> cors1.configurationSource(cors))
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }

    // Protected endpoints — JWT required
    @Bean
    @Order(2)
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, CorsConfigurationSource cors) {
        http
                .cors(cors1 -> cors1.configurationSource(cors))
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/ws/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/users/create").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/users/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/users/**").authenticated()
                        .pathMatchers(HttpMethod.PATCH, "/api/users/**").hasRole("ADMIN")
                        .pathMatchers("/api/orders/**").authenticated()
                        .pathMatchers("/api/products/**").authenticated()
                        .pathMatchers("/api/tasks/**").authenticated()
                        .pathMatchers("/api/notifications/**").authenticated()
                        .pathMatchers("/api/ai/**").authenticated()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(reactiveJwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(authenticationEntryPoint())
                );
        return http.build();
    }
}
