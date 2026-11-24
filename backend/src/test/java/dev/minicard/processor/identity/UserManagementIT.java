package dev.minicard.processor.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.minicard.processor.identity.repository.UserRepository;
import dev.minicard.processor.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
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
class UserManagementIT extends PostgresContainerSupport {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;

  private String adminToken;

  @BeforeEach
  void setUpAdmin() throws Exception {
    userRepository
        .findByEmailIgnoreCase("analyst.test@minicard.local")
        .ifPresent(userRepository::delete);

    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"admin@minicard.local\",\"password\":\"TestAdminPassword123!\"}"))
            .andExpect(status().isOk())
            .andReturn();

    adminToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
  }

  @Test
  void adminCanCreateListAndUpdateUsersWithRoleEnforcement() throws Exception {
    // 1. Anonymous access is rejected with 401
    mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());

    // 2. Admin creates a new analyst
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/users")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email": "analyst.test@minicard.local",
                          "displayName": "Test Analyst",
                          "role": "ANALYST",
                          "password": "Password12345!"
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("analyst.test@minicard.local"))
            .andExpect(jsonPath("$.role").value("ANALYST"))
            .andReturn();

    String userId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

    // 3. Duplicate email returns 409 EMAIL_ALREADY_USED
    mockMvc
        .perform(
            post("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "analyst.test@minicard.local",
                      "displayName": "Duplicate",
                      "role": "ANALYST",
                      "password": "Password12345!"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));

    // 4. Update analyst role to DISPUTE_OFFICER
    mockMvc
        .perform(
            patch("/api/v1/users/" + userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "displayName": "Promoted Officer",
                      "role": "DISPUTE_OFFICER",
                      "version": 0
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Promoted Officer"))
        .andExpect(jsonPath("$.role").value("DISPUTE_OFFICER"));

    // 5. Admin lists users
    mockMvc
        .perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page.totalElements").isNumber());
  }

  @Test
  void lastAdminProtectionPreventsDisablingSelf() throws Exception {
    // Get Admin ID from /auth/me
    MvcResult me =
        mockMvc
            .perform(
                get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();

    String adminId = JsonPath.read(me.getResponse().getContentAsString(), "$.id");

    // Attempt to disable self fails with LAST_ADMIN_PROTECTED
    mockMvc
        .perform(
            patch("/api/v1/users/" + adminId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "enabled": false,
                      "version": 0
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LAST_ADMIN_PROTECTED"));
  }
}
