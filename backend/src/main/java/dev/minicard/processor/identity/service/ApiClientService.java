package dev.minicard.processor.identity.service;

import dev.minicard.processor.audit.domain.ActorType;
import dev.minicard.processor.audit.service.AuditService;
import dev.minicard.processor.common.error.ConflictException;
import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.common.error.NotFoundException;
import dev.minicard.processor.identity.domain.ApiClient;
import dev.minicard.processor.identity.repository.ApiClientRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiClientService {

  private static final String KEY_PREFIX_HEADER = "mck_";
  private static final String PREFIX_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

  private final ApiClientRepository apiClientRepository;
  private final AuditService auditService;
  private final Clock clock;
  private final SecureRandom secureRandom;

  public ApiClientService(
      ApiClientRepository apiClientRepository, AuditService auditService, Clock clock) {
    this.apiClientRepository = apiClientRepository;
    this.auditService = auditService;
    this.clock = clock;
    this.secureRandom = new SecureRandom();
  }

  @Transactional(readOnly = true)
  public Page<ApiClient> listClients(Pageable pageable) {
    return apiClientRepository.findAll(pageable);
  }

  @Transactional
  public ApiClientCreatedResult createClient(
      String rawName, UUID actorId, String actorLabel, String ipAddress) {
    String name = rawName.trim();
    if (apiClientRepository.existsByNameIgnoreCase(name)) {
      throw new ConflictException(
          ErrorCode.NAME_ALREADY_USED, "API client name already exists: " + name);
    }

    String prefix = generatePrefix();
    byte[] secretBytes = new byte[32];
    secureRandom.nextBytes(secretBytes);
    String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
    String fullKey = KEY_PREFIX_HEADER + prefix + "_" + secret;
    String keyHash = hashKey(fullKey);

    Instant now = clock.instant();
    ApiClient client = new ApiClient(null, name, prefix, keyHash, actorId, now);
    ApiClient saved = apiClientRepository.save(client);

    auditService.record(
        ActorType.USER,
        actorId,
        actorLabel,
        "API_CLIENT_CREATED",
        "ApiClient",
        saved.getId(),
        ipAddress,
        "name=" + name + ",prefix=" + prefix);

    return new ApiClientCreatedResult(saved, fullKey);
  }

  @Transactional
  public ApiClient revokeClient(UUID id, UUID actorId, String actorLabel, String ipAddress) {
    ApiClient client =
        apiClientRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ErrorCode.API_CLIENT_NOT_FOUND, "API client not found with id: " + id));

    Instant now = clock.instant();
    client.revoke(now);
    ApiClient saved = apiClientRepository.save(client);

    auditService.record(
        ActorType.USER,
        actorId,
        actorLabel,
        "API_CLIENT_REVOKED",
        "ApiClient",
        saved.getId(),
        ipAddress,
        "name=" + saved.getName());

    return saved;
  }

  @Transactional
  public Optional<ApiClient> authenticate(String rawKey) {
    if (rawKey == null
        || rawKey.length() < 14
        || !rawKey.startsWith(KEY_PREFIX_HEADER)
        || rawKey.charAt(12) != '_') {
      return Optional.empty();
    }

    String prefix = rawKey.substring(4, 12);

    Optional<ApiClient> clientOpt = apiClientRepository.findByKeyPrefix(prefix);
    if (clientOpt.isEmpty()) {
      return Optional.empty();
    }

    ApiClient client = clientOpt.get();
    if (!client.isActive()) {
      return Optional.empty();
    }

    String computedHash = hashKey(rawKey);
    byte[] expectedBytes = HexFormat.of().parseHex(client.getKeyHash());
    byte[] actualBytes = HexFormat.of().parseHex(computedHash);

    if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
      return Optional.empty();
    }

    Instant now = clock.instant();
    if (client.getLastUsedAt() == null
        || Duration.between(client.getLastUsedAt(), now).toSeconds() > 60) {
      apiClientRepository.updateLastUsedAt(client.getId(), now);
    }

    return Optional.of(client);
  }

  private String generatePrefix() {
    StringBuilder sb = new StringBuilder(8);
    for (int i = 0; i < 8; i++) {
      int idx = secureRandom.nextInt(PREFIX_CHARS.length());
      sb.append(PREFIX_CHARS.charAt(idx));
    }
    return sb.toString();
  }

  private String hashKey(String fullKey) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(fullKey.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to hash API key", e);
    }
  }

  public record ApiClientCreatedResult(ApiClient client, String fullApiKey) {}
}
