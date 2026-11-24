package dev.minicard.processor.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

  private static final long serialVersionUID = 1L;
  private final ApiClientPrincipal principal;

  public ApiKeyAuthenticationToken(ApiClientPrincipal principal) {
    super(List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT")));
    this.principal = principal;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public ApiClientPrincipal getPrincipal() {
    return principal;
  }
}
