package de.caritas.cob.agencyservice.api.tenant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;


@AllArgsConstructor
@Component
@Slf4j
public class AccessTokenTenantResolver implements TenantResolver {

  private static final String TENANT_ID = "tenantId";
  private static final String AUTHORIZATION = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Optional<Long> resolve(HttpServletRequest request) {
    return resolveTenantIdFromTokenClaims(request);
  }

  private Optional<Long> resolveTenantIdFromTokenClaims(HttpServletRequest request) {
    Map<String, Object> claimMap = getClaimMap(request);
    log.debug("Found tenantId in claim : " + claimMap.toString());
    return getUserTenantIdAttribute(claimMap);
  }

  private Optional<Long> getUserTenantIdAttribute(Map<String, Object> claimMap) {
    if (claimMap.containsKey(TENANT_ID)) {
      Object tenantIdObject = claimMap.get(TENANT_ID);
      if (tenantIdObject instanceof Long tenantId) {
        return Optional.of(tenantId);
      }
      if (tenantIdObject instanceof Integer tenantId) {
        return Optional.of(Long.valueOf(tenantId));
      }
      if (tenantIdObject instanceof String tenantId) {
        try {
          return Optional.of(Long.parseLong(tenantId));
        } catch (NumberFormatException ignored) {
          return Optional.empty();
        }
      }
    }
    return Optional.empty();
  }

  private Map<String, Object> getClaimMap(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null) {
      Object principal = authentication.getPrincipal();
      if (principal instanceof Jwt jwt) {
        return jwt.getClaims();
      }
    }
    return getClaimMapFromAuthorizationHeader(request);
  }

  private Map<String, Object> getClaimMapFromAuthorizationHeader(HttpServletRequest request) {
    String authHeader = request.getHeader(AUTHORIZATION);
    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
      return Map.of();
    }
    String token = authHeader.substring(BEARER_PREFIX.length()).trim();
    String[] parts = token.split("\\.");
    if (parts.length < 2) {
      return Map.of();
    }
    try {
      byte[] decodedPayload = Base64.getUrlDecoder().decode(parts[1]);
      String payload = new String(decodedPayload, StandardCharsets.UTF_8);
      return objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      log.warn("Could not parse JWT payload from Authorization header for tenant resolution");
      return Map.of();
    }
  }
}
