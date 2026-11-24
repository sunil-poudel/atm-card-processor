package dev.minicard.processor.identity.repository;

import dev.minicard.processor.identity.domain.AppUser;
import dev.minicard.processor.identity.domain.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository
    extends JpaRepository<AppUser, UUID>, JpaSpecificationExecutor<AppUser> {

  Optional<AppUser> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  @Query("SELECT COUNT(u) FROM AppUser u WHERE u.role = :role AND u.enabled = true")
  long countByRoleAndEnabledTrue(UserRole role);
}
