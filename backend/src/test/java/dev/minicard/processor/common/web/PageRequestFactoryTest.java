package dev.minicard.processor.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.minicard.processor.common.error.BadRequestException;
import dev.minicard.processor.common.error.ErrorCode;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageRequestFactoryTest {

  private static final Set<String> ALLOWED_SORTS =
      Set.of("createdAt", "holderName", "balanceMinor");
  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

  @Test
  void createsDefaultPageRequestWhenParametersAreNull() {
    Pageable pageable = PageRequestFactory.create(null, null, null, DEFAULT_SORT, ALLOWED_SORTS);

    assertThat(pageable.getPageNumber()).isEqualTo(0);
    assertThat(pageable.getPageSize()).isEqualTo(20);
    assertThat(pageable.getSort()).isEqualTo(DEFAULT_SORT);
  }

  @Test
  void parsesValidPageSizeAndSortParameters() {
    Pageable pageable =
        PageRequestFactory.create(2, 50, "balanceMinor,asc", DEFAULT_SORT, ALLOWED_SORTS);

    assertThat(pageable.getPageNumber()).isEqualTo(2);
    assertThat(pageable.getPageSize()).isEqualTo(50);
    assertThat(pageable.getSort().getOrderFor("balanceMinor")).isNotNull();
    assertThat(pageable.getSort().getOrderFor("balanceMinor").getDirection())
        .isEqualTo(Sort.Direction.ASC);
  }

  @Test
  void rejectsNegativePageNumber() {
    assertThatThrownBy(() -> PageRequestFactory.create(-1, 20, null, DEFAULT_SORT, ALLOWED_SORTS))
        .isInstanceOf(BadRequestException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void rejectsZeroOrExcessivePageSize() {
    assertThatThrownBy(() -> PageRequestFactory.create(0, 0, null, DEFAULT_SORT, ALLOWED_SORTS))
        .isInstanceOf(BadRequestException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);

    assertThatThrownBy(() -> PageRequestFactory.create(0, 101, null, DEFAULT_SORT, ALLOWED_SORTS))
        .isInstanceOf(BadRequestException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void rejectsDisallowedSortProperty() {
    assertThatThrownBy(
            () -> PageRequestFactory.create(0, 20, "panEncrypted,asc", DEFAULT_SORT, ALLOWED_SORTS))
        .isInstanceOf(BadRequestException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void rejectsInvalidSortDirection() {
    assertThatThrownBy(
            () ->
                PageRequestFactory.create(0, 20, "createdAt,sideways", DEFAULT_SORT, ALLOWED_SORTS))
        .isInstanceOf(BadRequestException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
  }
}
