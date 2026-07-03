package de.caritas.cob.agencyservice.config;

import de.caritas.cob.agencyservice.api.authorization.Authority.AuthorityValue;
import de.caritas.cob.agencyservice.config.security.AuthorisationService;
import de.caritas.cob.agencyservice.config.security.JwtAuthConverter;
import de.caritas.cob.agencyservice.config.security.JwtAuthConverterProperties;
import de.caritas.cob.agencyservice.filter.HttpTenantFilter;
import de.caritas.cob.agencyservice.filter.StatelessCsrfFilter;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.keycloak.adapters.springsecurity.KeycloakConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;

/**
 * Provides the Keycloak/Spring Security configuration.
 */
@KeycloakConfiguration
@EnableMethodSecurity(
    prePostEnabled = true)
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  public static final String[] WHITE_LIST =
      new String[]{"/agencies/docs", "/agencies/docs/**", "/v2/api-docs", "/configuration/ui",
          "/swagger-resources/**", "/configuration/security", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**", "/actuator/health", "/actuator/health/**",
          "/internal/agencies", "/internal/agencies/**"};

  @Autowired
  AuthorisationService authorisationService;
  @Autowired
  JwtAuthConverterProperties jwtAuthConverterProperties;


  @Value("${csrf.cookie.property}")
  private String csrfCookieProperty;

  @Value("${csrf.header.property}")
  private String csrfHeaderProperty;

  @Autowired
  private Environment environment;

  @Autowired(required = false)
  @Nullable
  private HttpTenantFilter httpTenantFilter;

  @Value("${multitenancy.enabled}")
  private boolean multitenancy;

  /**
   * Configure spring security filter chain: disable default Spring Boot CSRF token behavior and add
   * custom {@link StatelessCsrfFilter}, set all sessions to be fully stateless, define necessary
   * Keycloak roles for specific REST API paths
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    var httpSecurity = http.csrf(csrf -> csrf.disable())
        .addFilterBefore(new StatelessCsrfFilter(csrfCookieProperty, csrfHeaderProperty),
            CsrfFilter.class);

    if (multitenancy) {
      httpSecurity = httpSecurity
          .addFilterAfter(httpTenantFilter, BearerTokenAuthenticationFilter.class);
    }

    httpSecurity
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers(WHITE_LIST).permitAll()
            // Allow public topics without auth
            .requestMatchers(HttpMethod.OPTIONS, "/service/topic/public", "/service/topic/public/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/service/topic/public", "/service/topic/public/**").permitAll()
            .requestMatchers(HttpMethod.OPTIONS, "/topic/public", "/topic/public/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/topic/public", "/topic/public/**").permitAll()
            .requestMatchers("/agencies").permitAll()
            .requestMatchers(HttpMethod.GET, "/agencyadmin/agencies")
            .hasAuthority(AuthorityValue.SEARCH_AGENCIES)
            .requestMatchers("/agencies/by-tenant")
            .hasAuthority(AuthorityValue.SEARCH_AGENCIES_WITHIN_TENANT)
            .requestMatchers("/agencyadmin/agencies/tenant/*")
            .access(new WebExpressionAuthorizationManager("hasAuthority('"
                + AuthorityValue.AGENCY_ADMIN + "') and hasAuthority('"
                + AuthorityValue.TENANT_ADMIN + "')"))
            .requestMatchers("/agencyadmin/controls", "/agencyadmin/controls/")
            .hasAuthority(AuthorityValue.GET_ALL_AGENCIES)
            .requestMatchers("/agencyadmin", "/agencyadmin/", "/agencyadmin/**")
            .hasAnyAuthority(AuthorityValue.AGENCY_ADMIN, AuthorityValue.RESTRICTED_AGENCY_ADMIN)
            .requestMatchers("/agencies/**").permitAll()
            .requestMatchers("/internal/agencies/**").permitAll()
            .anyRequest().denyAll())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(
            jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())));
    return httpSecurity.build();
  }

  @Bean
  public JwtAuthConverter jwtAuthConverter() {
    return new JwtAuthConverter(jwtAuthConverterProperties, authorisationService);
  }



}
