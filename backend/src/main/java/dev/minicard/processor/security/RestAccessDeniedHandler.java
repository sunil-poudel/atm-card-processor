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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  private final GlobalExceptionHandler exceptionHandler;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public RestAccessDeniedHandler(GlobalExceptionHandler exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {

    ProblemDetail problem =
        exceptionHandler.createProblemDetail(
            ErrorCode.FORBIDDEN,
            "Access is denied for the requested resource",
            List.of(),
            request.getRequestURI());

    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), problem);
  }
}
