package dev.minicard.processor.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.minicard.processor.support.PostgresContainerSupport;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiSnapshotTest extends PostgresContainerSupport {

  @Autowired private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName(
      "OpenAPI spec snapshot test: valid schemes, all summaries and error responses, matches docs/openapi.json")
  void testOpenApiSnapshot() throws Exception {
    MvcResult result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();

    String rawJson = result.getResponse().getContentAsString();
    ObjectMapper prettyMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    JsonNode root = prettyMapper.readTree(rawJson);

    // 1. Check security schemes
    JsonNode securitySchemes = root.path("components").path("securitySchemes");
    assertThat(securitySchemes.has("bearerAuth"))
        .as("OpenAPI must include bearerAuth security scheme")
        .isTrue();
    assertThat(securitySchemes.has("apiKeyAuth"))
        .as("OpenAPI must include apiKeyAuth security scheme")
        .isTrue();

    // 2. Check every operation has a summary and at least one error response
    JsonNode paths = root.path("paths");
    assertThat(paths.isObject()).isTrue();

    Iterator<Map.Entry<String, JsonNode>> fields = paths.fields();
    int operationCount = 0;
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> pathEntry = fields.next();
      String path = pathEntry.getKey();
      JsonNode pathItem = pathEntry.getValue();

      Iterator<Map.Entry<String, JsonNode>> opFields = pathItem.fields();
      while (opFields.hasNext()) {
        Map.Entry<String, JsonNode> opEntry = opFields.next();
        String method = opEntry.getKey();
        if ("parameters".equalsIgnoreCase(method) || "summary".equalsIgnoreCase(method)) {
          continue;
        }

        JsonNode operation = opEntry.getValue();
        operationCount++;

        String summary = operation.path("summary").asText(null);
        assertThat(summary)
            .as("Operation %s %s must have a non-blank summary", method, path)
            .isNotBlank();

        JsonNode responses = operation.path("responses");
        assertThat(responses.isObject()).isTrue();

        boolean hasErrorResponse = false;
        Iterator<String> responseCodes = responses.fieldNames();
        while (responseCodes.hasNext()) {
          String code = responseCodes.next();
          try {
            int statusCode = Integer.parseInt(code);
            if (statusCode >= 400) {
              hasErrorResponse = true;
              break;
            }
          } catch (NumberFormatException ignored) {
            if ("default".equalsIgnoreCase(code)) {
              hasErrorResponse = true;
              break;
            }
          }
        }

        assertThat(hasErrorResponse)
            .as("Operation %s %s must define at least one error response (>= 400)", method, path)
            .isTrue();
      }
    }

    assertThat(operationCount).as("Must document operations").isGreaterThanOrEqualTo(15);

    // 3. Snapshot file check and update mechanism
    String formattedGenerated = prettyMapper.writeValueAsString(root);
    Path snapshotPath = resolveSnapshotPath();

    boolean updateMode = Boolean.getBoolean("openapi.update") || !Files.exists(snapshotPath);
    if (updateMode) {
      if (snapshotPath.getParent() != null) {
        Files.createDirectories(snapshotPath.getParent());
      }
      Files.writeString(snapshotPath, formattedGenerated, StandardCharsets.UTF_8);
    } else {
      String expected = Files.readString(snapshotPath, StandardCharsets.UTF_8);
      JsonNode expectedNode = prettyMapper.readTree(expected);
      assertThat(root)
          .as("Generated OpenAPI spec must match committed docs/openapi.json")
          .isEqualTo(expectedNode);
    }
  }

  private Path resolveSnapshotPath() {
    File candidate = new File("docs/openapi.json");
    if (candidate.exists() || new File("docs").exists()) {
      return candidate.toPath();
    }
    return Path.of("../docs/openapi.json");
  }
}
