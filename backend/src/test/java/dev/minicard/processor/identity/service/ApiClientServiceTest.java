package dev.minicard.processor.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.minicard.processor.audit.service.AuditService;
import dev.minicard.processor.identity.domain.ApiClient;
import dev.minicard.processor.identity.repository.ApiClientRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiClientServiceTest {

  private ApiClientRepository apiClientRepository;
  private AuditService auditService;
  private Clock fixedClock;
  private ApiClientService apiClientService;

  @BeforeEach
  void setUp() {
    apiClientRepository = mock(ApiClientRepository.class);
    auditService = mock(AuditService.class);
    fixedClock = Clock.fixed(Instant.parse("2026-10-21T06:00:00Z"), ZoneOffset.UTC);
    apiClientService = new ApiClientService(apiClientRepository, auditService, fixedClock);
  }

  @Test
  void createClientGeneratesKeyWithMckPrefixAndReturnsFullKeyOnce() {
    when(apiClientRepository.existsByNameIgnoreCase("Test Client")).thenReturn(false);
    when(apiClientRepository.save(any(ApiClient.class))).thenAnswer(inv -> inv.getArgument(0));

    ApiClientService.ApiClientCreatedResult result =
        apiClientService.createClient(
            "Test Client", UUID.randomUUID(), "admin@minicard.local", "127.0.0.1");

    assertThat(result.client().getName()).isEqualTo("Test Client");
    assertThat(result.client().getKeyPrefix()).hasSize(8);
    assertThat(result.fullApiKey()).startsWith("mck_" + result.client().getKeyPrefix() + "_");
    assertThat(result.fullApiKey()).hasSize(56);
  }

  @Test
  void authenticateValidatesSha256HashInConstantTime() {
    when(apiClientRepository.existsByNameIgnoreCase("POS Client")).thenReturn(false);
    when(apiClientRepository.save(any(ApiClient.class))).thenAnswer(inv -> inv.getArgument(0));

    ApiClientService.ApiClientCreatedResult created =
        apiClientService.createClient(
            "POS Client", UUID.randomUUID(), "admin@minicard.local", "127.0.0.1");

    ApiClient client = created.client();
    when(apiClientRepository.findByKeyPrefix(client.getKeyPrefix()))
        .thenReturn(Optional.of(client));

    Optional<ApiClient> authSuccess = apiClientService.authenticate(created.fullApiKey());
    assertThat(authSuccess).isPresent();
    assertThat(authSuccess.get().getName()).isEqualTo("POS Client");

    // Invalid key with wrong secret
    Optional<ApiClient> authFailure =
        apiClientService.authenticate(
            "mck_" + client.getKeyPrefix() + "_wrongsecretwrongsecretwrongsecretwrongsec");
    assertThat(authFailure).isEmpty();
  }
}
