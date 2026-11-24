package dev.minicard.processor.common.web;

import dev.minicard.processor.common.error.BadRequestException;
import dev.minicard.processor.common.error.ErrorCode;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageRequestFactory {

  public static final int DEFAULT_PAGE = 0;
  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  private PageRequestFactory() {}

  public static Pageable create(
      Integer page,
      Integer size,
      String sortParam,
      Sort defaultSort,
      Set<String> allowedSortProperties) {

    int pageNumber = (page == null) ? DEFAULT_PAGE : page;
    if (pageNumber < 0) {
      throw new BadRequestException(
          ErrorCode.VALIDATION_FAILED, "Page number must be greater than or equal to 0");
    }

    int pageSize = (size == null) ? DEFAULT_SIZE : size;
    if (pageSize < 1 || pageSize > MAX_SIZE) {
      throw new BadRequestException(
          ErrorCode.VALIDATION_FAILED,
          "Page size must be between 1 and " + MAX_SIZE + " inclusive");
    }

    Sort sort = parseSort(sortParam, defaultSort, allowedSortProperties);
    return PageRequest.of(pageNumber, pageSize, sort);
  }

  private static Sort parseSort(
      String sortParam, Sort defaultSort, Set<String> allowedSortProperties) {
    if (sortParam == null || sortParam.isBlank()) {
      return defaultSort != null ? defaultSort : Sort.unsorted();
    }

    String[] parts = sortParam.split(",");
    String property = parts[0].trim();
    if (!allowedSortProperties.contains(property)) {
      throw new BadRequestException(
          ErrorCode.VALIDATION_FAILED, "Invalid sort property: " + property);
    }

    Sort.Direction direction = Sort.Direction.ASC;
    if (parts.length > 1) {
      String dirStr = parts[1].trim();
      if ("desc".equalsIgnoreCase(dirStr)) {
        direction = Sort.Direction.DESC;
      } else if (!"asc".equalsIgnoreCase(dirStr)) {
        throw new BadRequestException(
            ErrorCode.VALIDATION_FAILED, "Invalid sort direction: " + dirStr);
      }
    }

    return Sort.by(direction, property);
  }
}
