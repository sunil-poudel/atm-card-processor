package dev.minicard.processor.arch;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import dev.minicard.processor.cards.domain.Card;
import jakarta.persistence.Entity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(
    packages = "dev.minicard.processor",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureRulesTest {

  @ArchTest
  static final ArchRule rule1_web_does_not_depend_on_repository =
      noClasses()
          .that()
          .resideInAPackage("..web..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..repository..")
          .because("Controllers must go through services, never access repositories directly");

  @ArchTest
  static final ArchRule rule2_domain_does_not_depend_on_web_or_security =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework.web..", "..security..", "..repository..")
          .because(
              "Domain entities and types must not depend on web, security, or repository layers");

  @ArchTest
  static final ArchRule rule3_no_package_cycles =
      slices().matching("dev.minicard.processor.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule rule4_controllers_must_reside_in_web =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .resideInAPackage("..web..")
          .because("Controllers must reside in web packages");

  @ArchTest
  static final ArchRule rule5_web_endpoints_do_not_return_entities =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAPackage("..web..")
          .and()
          .arePublic()
          .should()
          .notHaveRawReturnType(are(annotatedWith(Entity.class)))
          .because("Web endpoints must return DTO records, never entities (non-negotiable #7)");

  @ArchTest
  static final ArchRule rule6_no_field_injection =
      noFields()
          .should()
          .beAnnotatedWith(Autowired.class)
          .because("Constructor injection must be used everywhere");

  @ArchTest
  static final ArchRule rule7_no_direct_time_calls =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "dev.minicard.processor.common.config..", "dev.minicard.processor.common.id..")
          .should()
          .callMethod(Instant.class, "now")
          .orShould()
          .callMethod(LocalDate.class, "now")
          .orShould()
          .callMethod(LocalDateTime.class, "now")
          .orShould()
          .callMethod(ZonedDateTime.class, "now")
          .orShould()
          .callMethod(System.class, "currentTimeMillis")
          .because("All time must come from an injected Clock (non-negotiable #2)");

  @ArchTest
  static final ArchRule rule8_no_standard_streams =
      GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

  @ArchTest
  static final ArchRule rule9_only_ledger_service_may_call_card_update_balance =
      noClasses()
          .that()
          .doNotHaveFullyQualifiedName("dev.minicard.processor.ledger.service.LedgerService")
          .should()
          .callMethod(Card.class, "updateBalance", long.class)
          .because(
              "LedgerService is the only class allowed to modify Card.balanceMinor (non-negotiable rule #3)");

  @ArchTest
  static final ArchRule rule10_no_util_helper_manager_names =
      noClasses()
          .should()
          .haveSimpleNameEndingWith("Util")
          .orShould()
          .haveSimpleNameEndingWith("Utils")
          .orShould()
          .haveSimpleNameEndingWith("Helper")
          .orShould()
          .haveSimpleNameEndingWith("Manager")
          .because("No *Util, *Helper, or *Manager classes allowed (non-negotiable #7)");

  @ArchTest
  static final ArchRule rule11_transactional_only_on_services =
      classes()
          .that()
          .areAnnotatedWith(Transactional.class)
          .should()
          .resideInAnyPackage("..service..", "dev.minicard.processor.disputes.service..")
          .because("@Transactional should only be applied to service layer classes");

  @ArchTest
  static final ArchRule rule12_repositories_in_repository_packages =
      classes()
          .that()
          .haveSimpleNameEndingWith("Repository")
          .should()
          .resideInAPackage("..repository..")
          .because("Repositories must reside in repository packages");

  @ArchTest
  static final ArchRule rule13_dtos_are_records =
      classes()
          .that()
          .resideInAPackage("..web.dto..")
          .and()
          .areNotEnums()
          .and()
          .areNotInterfaces()
          .should()
          .beRecords()
          .because("All DTOs must be Java records");
}
