package dev.minicard.processor.identity.domain;

import dev.minicard.processor.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser extends BaseEntity {

  @Column(name = "email", nullable = false, length = 254)
  private String email;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private UserRole role;

  @Column(name = "enabled", nullable = false)
  private boolean enabled = true;

  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts = 0;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  protected AppUser() {}

  public AppUser(
      UUID id,
      String email,
      String displayName,
      String passwordHash,
      UserRole role,
      boolean enabled,
      Instant now) {
    super(id, now, now);
    this.email = normalizeEmail(email);
    this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
    this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash cannot be null");
    this.role = Objects.requireNonNull(role, "role cannot be null");
    this.enabled = enabled;
    this.failedLoginAttempts = 0;
    this.lockedUntil = null;
  }

  public static String normalizeEmail(String email) {
    return Objects.requireNonNull(email, "email cannot be null").trim().toLowerCase();
  }

  public String getEmail() {
    return email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public UserRole getRole() {
    return role;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public int getFailedLoginAttempts() {
    return failedLoginAttempts;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public boolean isLocked(Instant now) {
    return lockedUntil != null && lockedUntil.isAfter(now);
  }

  public void recordLoginSuccess(Instant now) {
    this.failedLoginAttempts = 0;
    this.lockedUntil = null;
    setUpdatedAt(now);
  }

  public boolean recordLoginFailure(Instant now, int maxAttempts, int lockoutMinutes) {
    this.failedLoginAttempts++;
    setUpdatedAt(now);
    if (this.failedLoginAttempts >= maxAttempts) {
      this.lockedUntil = now.plus(Duration.ofMinutes(lockoutMinutes));
      return true;
    }
    return false;
  }

  public void update(String displayName, UserRole role, Boolean enabled, Instant now) {
    if (displayName != null && !displayName.isBlank()) {
      this.displayName = displayName.trim();
    }
    if (role != null) {
      this.role = role;
    }
    if (enabled != null) {
      this.enabled = enabled;
    }
    setUpdatedAt(now);
  }

  public void changePassword(String newPasswordHash, Instant now) {
    this.passwordHash = Objects.requireNonNull(newPasswordHash, "passwordHash cannot be null");
    setUpdatedAt(now);
  }
}
