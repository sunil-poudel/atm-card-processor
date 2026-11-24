package dev.minicard.processor.common.error;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.minicard.processor.common.web.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;
  private Clock fixedClock;

  @BeforeEach
  void setUp() {
    fixedClock = Clock.fixed(Instant.parse("2026-10-21T06:00:00Z"), ZoneOffset.UTC);
    GlobalExceptionHandler handler = new GlobalExceptionHandler(fixedClock);
    TestExceptionController controller = new TestExceptionController();

    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(handler)
            .addFilters(new RequestIdFilter())
            .build();
  }

  @Test
  void handlesApiExceptionWithMappedStatusAndCode() throws Exception {
    mockMvc
        .perform(get("/test/not-found").header("X-Request-Id", "req-123"))
        .andExpect(status().isNotFound())
        .andExpect(header().string("X-Request-Id", "req-123"))
        .andExpect(jsonPath("$.type", equalTo("urn:minicard:problem:card-not-found")))
        .andExpect(jsonPath("$.title", equalTo("Card not found")))
        .andExpect(jsonPath("$.status", equalTo(404)))
        .andExpect(jsonPath("$.code", equalTo("CARD_NOT_FOUND")))
        .andExpect(jsonPath("$.detail", equalTo("Card with id 123 does not exist")))
        .andExpect(jsonPath("$.requestId", equalTo("req-123")))
        .andExpect(jsonPath("$.timestamp", equalTo("2026-10-21T06:00:00Z")));
  }

  @Test
  void handlesValidationExceptionWithFieldErrors() throws Exception {
    mockMvc
        .perform(
            post("/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code", equalTo("VALIDATION_FAILED")))
        .andExpect(jsonPath("$.errors[0].field", equalTo("name")))
        .andExpect(jsonPath("$.errors[0].message", notNullValue()));
  }

  @Test
  void handlesMalformedJsonRequest() throws Exception {
    mockMvc
        .perform(
            post("/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-valid-json}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code", equalTo("MALFORMED_REQUEST")));
  }

  @Test
  void handlesOptimisticLockingFailure() throws Exception {
    mockMvc
        .perform(get("/test/optimistic-lock"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code", equalTo("OPTIMISTIC_LOCK_FAILURE")));
  }

  @Test
  void handlesDatabaseLockTimeoutWithRetryAfterHeader() throws Exception {
    mockMvc
        .perform(get("/test/lock-timeout"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "1"))
        .andExpect(jsonPath("$.code", equalTo("TEMPORARILY_UNAVAILABLE")));
  }

  @Test
  void handlesUnexpectedExceptionWithSafeGenericDetail() throws Exception {
    mockMvc
        .perform(get("/test/unexpected"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code", equalTo("INTERNAL_ERROR")))
        .andExpect(jsonPath("$.detail", equalTo("An unexpected error occurred")));
  }

  @RestController
  @RequestMapping("/test")
  static class TestExceptionController {

    @GetMapping("/not-found")
    void notFound() {
      throw new NotFoundException(ErrorCode.CARD_NOT_FOUND, "Card with id 123 does not exist");
    }

    @PostMapping("/validate")
    void validate(@Valid @RequestBody SampleRequest request) {}

    @GetMapping("/optimistic-lock")
    void optimisticLock() {
      throw new OptimisticLockingFailureException("Version conflict");
    }

    @GetMapping("/lock-timeout")
    void lockTimeout() {
      throw new CannotAcquireLockException("Lock wait timeout");
    }

    @GetMapping("/unexpected")
    void unexpected() {
      throw new RuntimeException("Internal db connection leak details that must not leak");
    }
  }

  record SampleRequest(@NotBlank String name) {}
}
