package dev.minicard.processor.fraud.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.minicard.processor.audit.domain.ActorType;
import dev.minicard.processor.audit.service.AuditService;
import dev.minicard.processor.common.error.BadRequestException;
import dev.minicard.processor.common.error.ConflictException;
import dev.minicard.processor.fraud.domain.FraudRule;
import dev.minicard.processor.fraud.domain.FraudRuleAction;
import dev.minicard.processor.fraud.domain.FraudRuleType;
import dev.minicard.processor.fraud.repository.FraudRuleRepository;
import dev.minicard.processor.fraud.web.dto.CreateFraudRuleRequest;
import dev.minicard.processor.fraud.web.dto.FraudRuleResponse;
import dev.minicard.processor.fraud.web.dto.UpdateFraudRuleRequest;
import dev.minicard.processor.security.CallerIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class FraudRuleServiceTest {

  private FraudRuleRepository fraudRuleRepository;
  private AuditService auditService;
  private Clock clock;
  private FraudRuleService service;

  private final Instant now = Instant.parse("2026-10-20T10:00:00Z");
  private final CallerIdentity caller =
      CallerIdentity.forUser(UUID.randomUUID(), "admin@minicard.local");

  @BeforeEach
  void setUp() {
    fraudRuleRepository = mock(FraudRuleRepository.class);
    auditService = mock(AuditService.class);
    clock = Clock.fixed(now, ZoneOffset.UTC);
    service = new FraudRuleService(fraudRuleRepository, auditService, clock);
  }

  @Test
  @DisplayName("parameter validation rejects invalid parameter combinations per type")
  void testParameterValidation() {
    // MAX_AMOUNT missing threshold
    assertThatThrownBy(
            () -> FraudRuleService.validateParameters(FraudRuleType.MAX_AMOUNT, null, null, null))
        .isInstanceOf(BadRequestException.class);

    // MAX_AMOUNT with windowSeconds
    assertThatThrownBy(
            () -> FraudRuleService.validateParameters(FraudRuleType.MAX_AMOUNT, 1000L, 60, null))
        .isInstanceOf(BadRequestException.class);

    // VELOCITY missing windowSeconds
    assertThatThrownBy(
            () -> FraudRuleService.validateParameters(FraudRuleType.VELOCITY, 5L, null, null))
        .isInstanceOf(BadRequestException.class);

    // VELOCITY windowSeconds out of bounds (> 86400)
    assertThatThrownBy(
            () -> FraudRuleService.validateParameters(FraudRuleType.VELOCITY, 5L, 90000, null))
        .isInstanceOf(BadRequestException.class);

    // BLOCKED_MCC with thresholdValue
    assertThatThrownBy(
            () ->
                FraudRuleService.validateParameters(
                    FraudRuleType.BLOCKED_MCC, 500L, null, List.of("7995")))
        .isInstanceOf(BadRequestException.class);

    // BLOCKED_MCC invalid 3-digit code
    assertThatThrownBy(
            () ->
                FraudRuleService.validateParameters(
                    FraudRuleType.BLOCKED_MCC, null, null, List.of("799")))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  @DisplayName("createRule rejects duplicate rule name")
  void testCreateDuplicateName() {
    when(fraudRuleRepository.existsByNameIgnoreCase("Existing Rule")).thenReturn(true);

    CreateFraudRuleRequest req =
        new CreateFraudRuleRequest(
            "Existing Rule",
            "desc",
            FraudRuleType.MAX_AMOUNT,
            true,
            1000L,
            null,
            null,
            40,
            FraudRuleAction.FLAG);

    assertThatThrownBy(() -> service.createRule(req, caller)).isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("createRule succeeds and records audit event")
  void testCreateRuleSuccess() {
    when(fraudRuleRepository.existsByNameIgnoreCase("New Rule")).thenReturn(false);
    when(fraudRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CreateFraudRuleRequest req =
        new CreateFraudRuleRequest(
            "New Rule",
            "desc",
            FraudRuleType.MAX_AMOUNT,
            true,
            5000L,
            null,
            null,
            40,
            FraudRuleAction.FLAG);

    FraudRuleResponse response = service.createRule(req, caller);
    assertThat(response.name()).isEqualTo("New Rule");
    assertThat(response.thresholdValue()).isEqualTo(5000L);

    verify(auditService)
        .record(
            eq(ActorType.USER),
            eq(caller.userId()),
            eq(caller.actorLabel()),
            eq("FRAUD_RULE_CREATED"),
            eq("FraudRule"),
            eq(response.id()),
            eq(null),
            any());
  }

  @Test
  @DisplayName("updateRule rejects version mismatch")
  void testUpdateOptimisticLock() {
    UUID ruleId = UUID.randomUUID();
    FraudRule existing =
        new FraudRule(
            ruleId,
            "Rule",
            "desc",
            FraudRuleType.MAX_AMOUNT,
            true,
            1000L,
            null,
            null,
            40,
            FraudRuleAction.FLAG,
            now,
            now,
            1L);

    when(fraudRuleRepository.findById(ruleId)).thenReturn(Optional.of(existing));

    UpdateFraudRuleRequest req =
        new UpdateFraudRuleRequest(
            "Rule",
            "desc",
            true,
            2000L,
            null,
            null,
            40,
            FraudRuleAction.FLAG,
            0L); // Version 0 instead of 1

    assertThatThrownBy(() -> service.updateRule(ruleId, req, caller))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  @Test
  @DisplayName("enable and disable change rule state and record audit")
  void testEnableAndDisable() {
    UUID ruleId = UUID.randomUUID();
    FraudRule existing =
        new FraudRule(
            ruleId,
            "Rule",
            "desc",
            FraudRuleType.MAX_AMOUNT,
            false,
            1000L,
            null,
            null,
            40,
            FraudRuleAction.FLAG,
            now,
            now,
            0L);

    when(fraudRuleRepository.findById(ruleId)).thenReturn(Optional.of(existing));
    when(fraudRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    FraudRuleResponse enabled = service.enableRule(ruleId, caller);
    assertThat(enabled.enabled()).isTrue();
    verify(auditService)
        .record(
            eq(ActorType.USER),
            eq(caller.userId()),
            eq(caller.actorLabel()),
            eq("FRAUD_RULE_ENABLED"),
            eq("FraudRule"),
            eq(ruleId),
            eq(null),
            any());

    FraudRuleResponse disabled = service.disableRule(ruleId, caller);
    assertThat(disabled.enabled()).isFalse();
    verify(auditService)
        .record(
            eq(ActorType.USER),
            eq(caller.userId()),
            eq(caller.actorLabel()),
            eq("FRAUD_RULE_DISABLED"),
            eq("FraudRule"),
            eq(ruleId),
            eq(null),
            any());
  }
}
