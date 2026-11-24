package dev.minicard.processor.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.minicard.processor.identity.service.AuthService;
import dev.minicard.processor.support.PostgresContainerSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIT extends PostgresContainerSupport {

  @Autowired private MockMvc mockMvc;

  @Test
  void fullLoginRefreshAndLogoutLifecycleWithReuseDetection() throws Exception {
    // 1. Login with Bootstrap Admin credentials configured in application-test.yml
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"admin@minicard.local\",\"password\":\"TestAdminPassword123!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.email").value("admin@minicard.local"))
            .andExpect(jsonPath("$.user.role").value("ADMIN"))
            .andExpect(header().exists(HttpHeaders.SET_COOKIE))
            .andReturn();

    String loginResponseBody = loginResult.getResponse().getContentAsString();
    String accessToken1 = JsonPath.read(loginResponseBody, "$.accessToken");
    Cookie refreshCookie1 = loginResult.getResponse().getCookie(AuthService.REFRESH_COOKIE_NAME);
    assertThat(refreshCookie1).isNotNull();
    assertThat(refreshCookie1.getValue()).isNotBlank();
    assertThat(refreshCookie1.isHttpOnly()).isTrue();

    // 2. GET /auth/me with accessToken1
    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("admin@minicard.local"))
        .andExpect(jsonPath("$.role").value("ADMIN"));

    // 3. Refresh without X-Requested-With fails with 403 Forbidden
    mockMvc
        .perform(post("/api/v1/auth/refresh").cookie(refreshCookie1))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    // 4. Refresh with X-Requested-With rotates token successfully
    MvcResult refreshResult =
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .cookie(refreshCookie1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(header().exists(HttpHeaders.SET_COOKIE))
            .andReturn();

    String accessToken2 =
        JsonPath.read(refreshResult.getResponse().getContentAsString(), "$.accessToken");
    Cookie refreshCookie2 = refreshResult.getResponse().getCookie(AuthService.REFRESH_COOKIE_NAME);
    assertThat(accessToken2).isNotEqualTo(accessToken1);
    assertThat(refreshCookie2).isNotNull();
    assertThat(refreshCookie2.getValue()).isNotEqualTo(refreshCookie1.getValue());

    // 5. Presenting old refreshCookie1 triggers REUSE DETECTION: entire family revoked
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(refreshCookie1))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

    // 6. Successor refreshCookie2 is now also revoked due to family revocation
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(refreshCookie2))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

    // 7. Logout endpoint clears cookie
    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(refreshCookie2))
        .andExpect(status().isNoContent())
        .andExpect(header().exists(HttpHeaders.SET_COOKIE));
  }
}
