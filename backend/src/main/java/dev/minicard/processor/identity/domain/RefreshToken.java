package dev.minicard.processor.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private AppUser user;

  @Column(name = "family_id", nullable = false, updatable = false)
  private UUID familyId;

  @Column(name = "token_hash", nullable = false, length = 64, unique = true, updatable = false)
  private String tokenHash;

  @Column(name = "issued_at", nullable = false, updatable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "replaced_by_id")
  private UUID replacedById;

  @Column(name = "user_agent", length = 200)
  private String userAgent;

  protected RefreshToken() {}

  public RefreshToken(
      UUID id,
      AppUser user,
      UUID familyId,
      String tokenHash,
      Instant issuedAt,
      Instant expiresAt,
      String userAgent) {
    this.id = id;
    this.user = Objects.requireNonNull(user, "user cannot be null");
    this.familyId = Objects.requireNonNull(familyId, "familyId cannot be null");
    this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash cannot be null");
    this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt cannot be null");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
    this.userAgent = userAgent;
  }

  public UUID getId() {
    return id;
  }

  public AppUser getUser() {
    return user;
  }

  public UUID getFamilyId() {
    return familyId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public UUID getReplacedById() {
    return replacedById;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean isExpired(Instant now) {
    return expiresAt.isBefore(now);
  }

  public boolean isValid(Instant now) {
    return !isRevoked() && !isExpired(now);
  }

  public void revoke(Instant now) {
    if (this.revokedAt == null) {
      this.revokedAt = now;
    }
  }

  public void replaceWith(UUID successorId, Instant now) {
    this.replacedById = successorId;
    revoke(now);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RefreshToken that)) return false;
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
