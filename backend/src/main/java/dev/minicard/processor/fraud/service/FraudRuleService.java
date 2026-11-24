package dev.minicard.processor.fraud.service;

import dev.minicard.processor.audit.domain.ActorType;
import dev.minicard.processor.audit.service.AuditService;
import dev.minicard.processor.common.error.BadRequestException;
import dev.minicard.processor.common.error.ConflictException;
import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.common.error.NotFoundException;
import dev.minicard.processor.common.id.UuidV7;
import dev.minicard.processor.fraud.domain.FraudRule;
import dev.minicard.processor.fraud.domain.FraudRuleType;
import dev.minicard.processor.fraud.repository.FraudRuleRepository;
import dev.minicard.processor.fraud.web.dto.CreateFraudRuleRequest;
import dev.minicard.processor.fraud.web.dto.FraudRuleResponse;
import dev.minicard.processor.fraud.web.dto.UpdateFraudRuleRequest;
import dev.minicard.processor.security.CallerIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FraudRuleService {

  private final FraudRuleRepository fraudRuleRepository;
  private final AuditService auditService;
  private final Clock clock;

  public FraudRuleService(
      FraudRuleRepository fraudRuleRepository, AuditService auditService, Clock clock) {
    this.fraudRuleRepository = fraudRuleRepository;
    this.auditService = auditService;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<FraudRuleResponse> listRules() {
    return fraudRuleRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
        .map(FraudRuleResponse::fromEntity)
        .toList();
  }

  @Transactional(readOnly = true)
  public FraudRuleResponse getRule(UUID id) {
    FraudRule rule =
        fraudRuleRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ErrorCode.FRAUD_RULE_NOT_FOUND, "Fraud rule not found: " + id));
    return FraudRuleResponse.fromEntity(rule);
  }

  @Transactional
  public FraudRuleResponse createRule(CreateFraudRuleRequest request, CallerIdentity caller) {
    String trimmedName = request.name().trim();
    if (fraudRuleRepository.existsByNameIgnoreCase(trimmedName)) {
      throw new ConflictException(
          ErrorCode.NAME_ALREADY_USED, "Fraud rule name already exists: " + trimmedName);
    }

    validateParameters(
        request.type(), request.thresholdValue(), request.windowSeconds(), request.mccCodes());

    String mccCodesSerialized =
        request.type() == FraudRuleType.BLOCKED_MCC ? formatMccCodes(request.mccCodes()) : null;

    Instant now = clock.instant();
    FraudRule rule =
        new FraudRule(
            UuidV7.newId(clock),
            trimmedName,
            request.description() != null ? request.description().trim() : null,
            request.type(),
            request.enabled() != null ? request.enabled() : true,
            request.thresholdValue(),
            request.windowSeconds(),
            mccCodesSerialized,
            request.scorePoints(),
            request.action(),
            now,
            now,
            null);

    rule = fraudRuleRepository.save(rule);

    recordAudit(
        caller,
        "FRAUD_RULE_CREATED",
        rule.getId(),
        "Created rule " + rule.getName() + " [" + rule.toSummaryString() + "]");

    return FraudRuleResponse.fromEntity(rule);
  }

  @Transactional
  public FraudRuleResponse updateRule(
      UUID id, UpdateFraudRuleRequest request, CallerIdentity caller) {
    FraudRule rule =
        fraudRuleRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ErrorCode.FRAUD_RULE_NOT_FOUND, "Fraud rule not found: " + id));

    if (!Objects.equals(rule.getVersion(), request.version())) {
      throw new OptimisticLockingFailureException(
          "Fraud rule version mismatch: expected "
              + rule.getVersion()
              + " but received "
              + request.version());
    }

    String trimmedName = request.name().trim();
    if (fraudRuleRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
      throw new ConflictException(
          ErrorCode.NAME_ALREADY_USED, "Fraud rule name already exists: " + trimmedName);
    }

    validateParameters(
        rule.getRuleType(), request.thresholdValue(), request.windowSeconds(), request.mccCodes());

    String beforeSummary = rule.toSummaryString();

    String mccCodesSerialized =
        rule.getRuleType() == FraudRuleType.BLOCKED_MCC ? formatMccCodes(request.mccCodes()) : null;

    rule.setName(trimmedName);
    rule.setDescription(request.description() != null ? request.description().trim() : null);
    if (request.enabled() != null) {
      rule.setEnabled(request.enabled());
    }
    rule.setThresholdValue(request.thresholdValue());
    rule.setWindowSeconds(request.windowSeconds());
    rule.setMccCodes(mccCodesSerialized);
    rule.setScorePoints(request.scorePoints());
    rule.setAction(request.action());
    rule.setUpdatedAt(clock.instant());

    rule = fraudRuleRepository.save(rule);

    String afterSummary = rule.toSummaryString();
    recordAudit(
        caller,
        "FRAUD_RULE_UPDATED",
        rule.getId(),
        "Updated rule "
            + rule.getName()
            + " from ["
            + beforeSummary
            + "] to ["
            + afterSummary
            + "]");

    return FraudRuleResponse.fromEntity(rule);
  }

  @Transactional
  public FraudRuleResponse enableRule(UUID id, CallerIdentity caller) {
    FraudRule rule =
        fraudRuleRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ErrorCode.FRAUD_RULE_NOT_FOUND, "Fraud rule not found: " + id));

    rule.setEnabled(true);
    rule.setUpdatedAt(clock.instant());
    rule = fraudRuleRepository.save(rule);

    recordAudit(caller, "FRAUD_RULE_ENABLED", rule.getId(), "Enabled fraud rule " + rule.getName());

    return FraudRuleResponse.fromEntity(rule);
  }

  @Transactional
  public FraudRuleResponse disableRule(UUID id, CallerIdentity caller) {
    FraudRule rule =
        fraudRuleRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ErrorCode.FRAUD_RULE_NOT_FOUND, "Fraud rule not found: " + id));

    rule.setEnabled(false);
    rule.setUpdatedAt(clock.instant());
    rule = fraudRuleRepository.save(rule);

    recordAudit(
        caller, "FRAUD_RULE_DISABLED", rule.getId(), "Disabled fraud rule " + rule.getName());

    return FraudRuleResponse.fromEntity(rule);
  }

  public static void validateParameters(
      FraudRuleType type, Long thresholdValue, Integer windowSeconds, List<String> mccCodes) {
    switch (type) {
      case MAX_AMOUNT -> {
        if (thresholdValue == null || thresholdValue <= 0) {
          throw new BadRequestException(
              ErrorCode.VALIDATION_FAILED, "MAX_AMOUNT rule requires thresholdValue > 0");
        }
        if (windowSeconds != null) {
          throw new BadRequestException(
              ErrorCode.VALIDATION_FAILED, "MAX_AMOUNT rule cannot have windowSeconds");
        }
        if (mccCodes != null && !mccCodes.isEmpty()) {
          throw new BadRequestException(
              ErrorCode.VALIDATION_FAILED, "MAX_AMOUNT rule cannot have mccCodes");
        }
      }
      case VELOCITY -> {
        if (thresholdValue == null || thresholdValue < 1) {
          throw new BadRequestException(
              ErrorCode.VALIDATION_FAILED, "VELOCITY rule requires thresholdValue >= 1");
        }
        if (windowSeconds == null || windowSeconds < 1 || windowSeconds > 86400) {
          throw new BadRequestException(
              ErrorCode.VALIDATION_FAILED,
              "VELOCITY rule requires windowSeconds between 1 and 86400");
        }
        if (mccCodes != null && !mccCodes.isEmpty()) {
          throw new BadRequestException(
              ErrorCode.VALIDATION_FAILED, "VELOCITY rule cannot have mccCodes");
        }
      }
      case BLOCKED_MCC -> {
        if (thresholdValue != null) {
          throw new BadRequestException(
              ErrorCode.VALIDATION_FAILED, "BLOCKED_MCC rule cannot have thresholdValue");
        }
        if (windowSeconds != null) {
          throw new BadRequestException(
              ErrorCode.VALIDATION_FAILED, "BLOCKED_MCC rule cannot have windowSeconds");
        }
        if (mccCodes == null || mccCodes.isEmpty()) {
          throw new BadRequestException(
              ErrorCode.VALIDATION_FAILED, "BLOCKED_MCC rule requires at least one MCC code");
        }
        if (mccCodes.size() > 50) {
          throw new BadRequestException(
              ErrorCode.VALIDATION_FAILED, "BLOCKED_MCC rule cannot exceed 50 MCC codes");
        }
        for (String mcc : mccCodes) {
          if (mcc == null || !mcc.trim().matches("^[0-9]{4}$")) {
            throw new BadRequestException(
                ErrorCode.VALIDATION_FAILED, "MCC codes must be exactly 4 digits: " + mcc);
          }
        }
      }
    }
  }

  public static String formatMccCodes(List<String> mccCodes) {
    if (mccCodes == null || mccCodes.isEmpty()) {
      return null;
    }
    return mccCodes.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .distinct()
        .sorted()
        .collect(Collectors.joining(","));
  }

  private void recordAudit(CallerIdentity caller, String action, UUID ruleId, String details) {
    if (caller != null) {
      ActorType actorType = caller.apiClientId() != null ? ActorType.API_CLIENT : ActorType.USER;
      UUID actorId = caller.apiClientId() != null ? caller.apiClientId() : caller.userId();
      auditService.record(
          actorType, actorId, caller.actorLabel(), action, "FraudRule", ruleId, null, details);
    } else {
      auditService.record(
          ActorType.SYSTEM, null, "SYSTEM", action, "FraudRule", ruleId, null, details);
    }
  }
}
