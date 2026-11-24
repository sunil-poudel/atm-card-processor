package dev.minicard.processor.security;

import dev.minicard.processor.identity.domain.ApiClient;
import dev.minicard.processor.identity.service.ApiClientService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  public static final String API_KEY_HEADER = "X-API-Key";
  public static final String AUTHORIZE_PATH = "/api/v1/authorize";

  private final ApiClientService apiClientService;

  public ApiKeyAuthenticationFilter(ApiClientService apiClientService) {
    this.apiClientService = apiClientService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String apiKey = request.getHeader(API_KEY_HEADER);
    if (apiKey != null && !apiKey.isBlank()) {
      String uri = request.getRequestURI();
      String method = request.getMethod();

      if ("POST".equalsIgnoreCase(method) && AUTHORIZE_PATH.equals(uri)) {
        Optional<ApiClient> clientOpt = apiClientService.authenticate(apiKey);
        if (clientOpt.isPresent()) {
          ApiClient client = clientOpt.get();
          ApiClientPrincipal principal =
              new ApiClientPrincipal(client.getId(), client.getName(), client.getKeyPrefix());
          ApiKeyAuthenticationToken authentication = new ApiKeyAuthenticationToken(principal);
          SecurityContextHolder.getContext().setAuthentication(authentication);
        }
      }
    }

    filterChain.doFilter(request, response);
  }
}
