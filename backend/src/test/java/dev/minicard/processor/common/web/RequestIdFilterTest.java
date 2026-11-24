package dev.minicard.processor.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private RequestIdFilter filter;

  @BeforeEach
  void setUp() {
    filter = new RequestIdFilter();
    MDC.clear();
  }

  @Test
  void incomingRequestIdIsPreservedAndEchoed() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "custom-request-id-123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    final String[] capturedMdc = new String[1];
    FilterChain chain = (req, res) -> capturedMdc[0] = MDC.get(RequestIdFilter.MDC_KEY);

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
        .isEqualTo("custom-request-id-123");
    assertThat(capturedMdc[0]).isEqualTo("custom-request-id-123");
    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull(); // Cleared in finally
  }

  @Test
  void missingRequestIdGeneratesNewUuidAndEchoes() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    final String[] capturedMdc = new String[1];
    FilterChain chain = (req, res) -> capturedMdc[0] = MDC.get(RequestIdFilter.MDC_KEY);

    filter.doFilter(request, response, chain);

    String generatedId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
    assertThat(generatedId).isNotBlank();
    assertThat(UUID.fromString(generatedId)).isNotNull();
    assertThat(capturedMdc[0]).isEqualTo(generatedId);
    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void blankOrTooLongRequestIdIsReplaced() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "a".repeat(65)); // > 64 chars
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    String echoedId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
    assertThat(echoedId).isNotEqualTo("a".repeat(65));
    assertThat(UUID.fromString(echoedId)).isNotNull();
  }
}
