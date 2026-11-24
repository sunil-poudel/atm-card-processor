package dev.minicard.processor.meta.web;

import dev.minicard.processor.common.reference.ReferenceData;
import dev.minicard.processor.meta.web.dto.ReferenceDataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meta")
@Tag(name = "Reference Data", description = "Static reference data catalogs")
public class ReferenceDataController {

  private static final ReferenceDataResponse RESPONSE =
      new ReferenceDataResponse(
          ReferenceData.RESPONSE_CODES,
          ReferenceData.DECLINE_REASONS,
          ReferenceData.MCCS,
          ReferenceData.CURRENCIES,
          ReferenceData.DISPUTE_REASONS);

  @GetMapping("/reference-data")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Get system reference data catalogs")
  public ResponseEntity<ReferenceDataResponse> getReferenceData() {
    return ResponseEntity.ok(RESPONSE);
  }
}
