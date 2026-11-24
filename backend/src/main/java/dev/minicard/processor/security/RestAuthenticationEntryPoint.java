package dev.minicard.processor.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.common.error.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final GlobalExceptionHandler exceptionHandler;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public RestAuthenticationEntryPoint(GlobalExceptionHandler exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {

    ProblemDetail problem =
        exceptionHandler.createProblemDetail(
            ErrorCode.UNAUTHENTICATED,
            "Full authentication is required to access this resource",
            List.of(),
            request.getRequestURI());

    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), problem);
  }
}
