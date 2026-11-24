package dev.minicard.processor.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.minicard.processor.common.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final AppProperties appProperties;
  private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
  private final RestAuthenticationEntryPoint authenticationEntryPoint;
  private final RestAccessDeniedHandler accessDeniedHandler;
  private final Clock clock;

  public SecurityConfig(
      AppProperties appProperties,
      ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
      RestAuthenticationEntryPoint authenticationEntryPoint,
      RestAccessDeniedHandler accessDeniedHandler,
      Clock clock) {
    this.appProperties = appProperties;
    this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
    this.authenticationEntryPoint = authenticationEntryPoint;
    this.accessDeniedHandler = accessDeniedHandler;
    this.clock = clock;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {

    // CSRF is disabled because all client requests use non-browser standard headers
    // (Bearer Authorization or X-Requested-With on cookie endpoints) and cookies are
    // SameSite=Strict.
    // See docs/06-SECURITY.md section 3.3 and ADR 0008.
    http.csrf(AbstractHttpConfigurer::disable);

    http.cors(cors -> cors.configurationSource(corsConfigurationSource()));

    http.sessionManagement(
        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    http.headers(
        headers ->
            headers
                .cacheControl(HeadersConfigurer.CacheControlConfig::disable)
                .contentTypeOptions(HeadersConfigurer.ContentTypeOptionsConfig::disable)
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .referrerPolicy(
                    referrer ->
                        referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));

    http.exceptionHandling(
        exceptions ->
            exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));

    http.authorizeHttpRequests(
        auth ->
            auth.requestMatchers(
                    "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout")
                .permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                .permitAll()
                .requestMatchers("/actuator/**")
                .hasRole("ADMIN")
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                .permitAll()
                .requestMatchers("/api/v1/users/**")
                .hasRole("ADMIN")
                .requestMatchers("/api/v1/api-clients/**")
                .hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/authorize")
                .hasAnyRole("API_CLIENT", "ADMIN")
                .anyRequest()
                .authenticated());

    http.oauth2ResourceServer(
        oauth2 ->
            oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));

    http.addFilterBefore(apiKeyAuthenticationFilter, BearerTokenAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          String role = jwt.getClaimAsString("role");
          if (role != null && !role.isBlank()) {
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
          }
          return Collections.emptyList();
        });
    return converter;
  }

  @Bean
  public SecretKey jwtSecretKey() {
    String secret = appProperties.security().jwt().secret();
    byte[] keyBytes;
    try {
      keyBytes = Base64.getDecoder().decode(secret);
      if (keyBytes.length < 32) {
        keyBytes = secret.getBytes(StandardCharsets.UTF_8);
      }
    } catch (IllegalArgumentException e) {
      keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    if (keyBytes.length < 32) {
      throw new IllegalStateException("JWT secret key must be at least 256 bits (32 bytes)");
    }
    return new SecretKeySpec(keyBytes, "HmacSHA256");
  }

  @Bean
  public JwtEncoder jwtEncoder(SecretKey secretKey) {
    JWKSource<SecurityContext> jwkSource = new ImmutableSecret<>(secretKey);
    return new NimbusJwtEncoder(jwkSource);
  }

  @Bean
  public JwtDecoder jwtDecoder(SecretKey secretKey) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();

    OAuth2TokenValidator<Jwt> issuerValidator = new JwtIssuerValidator("minicard-processor");
    JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ofSeconds(30));
    timestampValidator.setClock(clock);
    OAuth2TokenValidator<Jwt> audienceValidator =
        new JwtClaimValidator<List<String>>(
            "aud", aud -> aud != null && aud.contains("minicard-dashboard"));

    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            issuerValidator, timestampValidator, audienceValidator));
    return decoder;
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    List<String> allowedOrigins = appProperties.security().corsAllowedOrigins();

    if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
      configuration.setAllowedOrigins(allowedOrigins);
      configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
      configuration.setAllowedHeaders(
          List.of(
              "Authorization",
              "Content-Type",
              "Idempotency-Key",
              "X-Request-Id",
              "X-Requested-With",
              "X-API-Key"));
      configuration.setExposedHeaders(
          List.of("Idempotent-Replayed", "X-Request-Id", "Content-Disposition"));
      configuration.setAllowCredentials(true);
      configuration.setMaxAge(Duration.ofHours(1));
    } else {
      configuration.setAllowedOrigins(List.of());
      configuration.setAllowCredentials(false);
    }

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
