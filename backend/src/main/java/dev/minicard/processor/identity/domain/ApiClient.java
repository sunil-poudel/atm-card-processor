package dev.minicard.processor.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "api_clients")
public class ApiClient {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false, length = 100, unique = true)
  private String name;

  @Column(name = "key_prefix", nullable = false, length = 12, unique = true, updatable = false)
  private String keyPrefix;

  @Column(name = "key_hash", nullable = false, length = 64, updatable = false)
  private String keyHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private ApiClientStatus status;

  @Column(name = "created_by", updatable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version = 0L;

  protected ApiClient() {}

  public ApiClient(
      UUID id, String name, String keyPrefix, String keyHash, UUID createdBy, Instant now) {
    this.id = id;
    this.name = Objects.requireNonNull(name, "name cannot be null").trim();
    this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix cannot be null");
    this.keyHash = Objects.requireNonNull(keyHash, "keyHash cannot be null");
    this.status = ApiClientStatus.ACTIVE;
    this.createdBy = createdBy;
    this.createdAt = Objects.requireNonNull(now, "now cannot be null");
    this.version = 0L;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getKeyPrefix() {
    return keyPrefix;
  }

  public String getKeyHash() {
    return keyHash;
  }

  public ApiClientStatus getStatus() {
    return status;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  public Long getVersion() {
    return version;
  }

  public boolean isActive() {
    return status == ApiClientStatus.ACTIVE;
  }

  public void revoke(Instant now) {
    if (this.status != ApiClientStatus.REVOKED) {
      this.status = ApiClientStatus.REVOKED;
      this.revokedAt = now;
    }
  }

  public void updateLastUsedAt(Instant now) {
    this.lastUsedAt = now;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ApiClient that)) return false;
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
