package dev.minicard.processor.identity.service;

import dev.minicard.processor.audit.domain.ActorType;
import dev.minicard.processor.audit.service.AuditService;
import dev.minicard.processor.common.error.ConflictException;
import dev.minicard.processor.common.error.ErrorCode;
import dev.minicard.processor.common.error.NotFoundException;
import dev.minicard.processor.identity.domain.AppUser;
import dev.minicard.processor.identity.domain.UserRole;
import dev.minicard.processor.identity.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicyValidator passwordPolicyValidator;
  private final AuditService auditService;
  private final Clock clock;

  public UserService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      PasswordPolicyValidator passwordPolicyValidator,
      AuditService auditService,
      Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicyValidator = passwordPolicyValidator;
    this.auditService = auditService;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Page<AppUser> listUsers(Pageable pageable) {
    return userRepository.findAll(pageable);
  }

  @Transactional(readOnly = true)
  public AppUser getUser(UUID id) {
    return userRepository
        .findById(id)
        .orElseThrow(
            () -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found with id: " + id));
  }

  @Transactional
  public AppUser createUser(
      String rawEmail,
      String displayName,
      UserRole role,
      String rawPassword,
      UUID actorId,
      String actorLabel,
      String ipAddress) {

    String email = AppUser.normalizeEmail(rawEmail);
    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw new ConflictException(
          ErrorCode.EMAIL_ALREADY_USED, "Email is already in use: " + email);
    }

    passwordPolicyValidator.validate(rawPassword, email);
    String passwordHash = passwordEncoder.encode(rawPassword);
    Instant now = clock.instant();

    AppUser user = new AppUser(null, email, displayName, passwordHash, role, true, now);
    AppUser saved = userRepository.save(user);

    auditService.record(
        ActorType.USER,
        actorId,
        actorLabel,
        "USER_CREATED",
        "AppUser",
        saved.getId(),
        ipAddress,
        "role=" + role.name() + ",email=" + email);

    return saved;
  }

  @Transactional
  public AppUser updateUser(
      UUID id,
      String displayName,
      UserRole newRole,
      Boolean newEnabled,
      String newRawPassword,
      UUID actorId,
      String actorLabel,
      String ipAddress) {

    AppUser user = getUser(id);
    Instant now = clock.instant();

    boolean demotingAdmin =
        user.getRole() == UserRole.ADMIN && newRole != null && newRole != UserRole.ADMIN;
    boolean disablingAdmin =
        user.getRole() == UserRole.ADMIN && Boolean.FALSE.equals(newEnabled) && user.isEnabled();

    if (demotingAdmin || disablingAdmin) {
      if (user.getId().equals(actorId)) {
        throw new ConflictException(
            ErrorCode.LAST_ADMIN_PROTECTED,
            "Cannot disable or demote your own administrator account");
      }
      long activeAdmins = userRepository.countByRoleAndEnabledTrue(UserRole.ADMIN);
      if (activeAdmins <= 1) {
        throw new ConflictException(
            ErrorCode.LAST_ADMIN_PROTECTED,
            "Cannot disable or demote the last remaining active administrator");
      }
    }

    if (newRawPassword != null && !newRawPassword.isBlank()) {
      passwordPolicyValidator.validate(newRawPassword, user.getEmail());
      user.changePassword(passwordEncoder.encode(newRawPassword), now);
    }

    user.update(displayName, newRole, newEnabled, now);
    AppUser saved = userRepository.save(user);

    auditService.record(
        ActorType.USER,
        actorId,
        actorLabel,
        "USER_UPDATED",
        "AppUser",
        saved.getId(),
        ipAddress,
        "role=" + saved.getRole() + ",enabled=" + saved.isEnabled());

    return saved;
  }
}
