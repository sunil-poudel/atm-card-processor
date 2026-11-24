package dev.minicard.processor.meta;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.minicard.processor.support.PostgresContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReferenceDataIT extends PostgresContainerSupport {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("Anonymous access to reference data returns 401")
  void testAnonymousForbidden() throws Exception {
    mockMvc.perform(get("/api/v1/meta/reference-data")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = "OPERATOR")
  @DisplayName("Authenticated user gets full reference data catalogs")
  void testReferenceDataReturned() throws Exception {
    mockMvc
        .perform(get("/api/v1/meta/reference-data"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.responseCodes.length()").value(8))
        .andExpect(jsonPath("$.responseCodes[0].code").value("00"))
        .andExpect(jsonPath("$.responseCodes[0].message").value("Approved"))
        .andExpect(jsonPath("$.declineReasons.length()").value(8))
        .andExpect(jsonPath("$.declineReasons[0].reason").value("CARD_NOT_ACTIVE"))
        .andExpect(jsonPath("$.mccs.length()").value(20))
        .andExpect(jsonPath("$.currencies.length()").value(2))
        .andExpect(jsonPath("$.currencies[0].code").value("NPR"))
        .andExpect(jsonPath("$.currencies[0].minorUnits").value(2))
        .andExpect(jsonPath("$.currencies[1].code").value("USD"))
        .andExpect(jsonPath("$.disputeReasons.length()").value(5))
        .andExpect(jsonPath("$.disputeReasons[0]").value("UNAUTHORIZED_TRANSACTION"));
  }
}
