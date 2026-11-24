package dev.minicard.processor.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("ATM Card Processor API")
                .version("1.0.0")
                .description("Production-style prepaid card issuer-processing system"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Staff JWT access token"))
                .addSecuritySchemes(
                    "apiKeyAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-Key")
                        .description("API client key for POS terminal authorization")));
  }

  @Bean
  public OpenApiCustomizer globalErrorResponsesCustomizer() {
    return openApi -> {
      ApiResponse badRequest = new ApiResponse().description("Validation or malformed request");
      ApiResponse unauthorized = new ApiResponse().description("Authentication required");
      ApiResponse forbidden = new ApiResponse().description("Access denied");
      ApiResponse internalError = new ApiResponse().description("Internal server error");

      if (openApi.getPaths() != null) {
        openApi
            .getPaths()
            .values()
            .forEach(
                pathItem ->
                    pathItem
                        .readOperations()
                        .forEach(
                            operation -> {
                              ApiResponses responses = operation.getResponses();
                              if (responses != null) {
                                responses.addApiResponse("400", badRequest);
                                responses.addApiResponse("401", unauthorized);
                                responses.addApiResponse("403", forbidden);
                                responses.addApiResponse("500", internalError);
                              }
                            }));
      }
    };
  }
}
