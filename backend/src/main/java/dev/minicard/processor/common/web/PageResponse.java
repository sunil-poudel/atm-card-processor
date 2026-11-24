package dev.minicard.processor.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> content, PageMetadata page) {

  public static <T> PageResponse<T> from(Page<T> springPage) {
    return new PageResponse<>(
        springPage.getContent(),
        new PageMetadata(
            springPage.getNumber(),
            springPage.getSize(),
            springPage.getTotalElements(),
            springPage.getTotalPages()));
  }

  public record PageMetadata(int number, int size, long totalElements, int totalPages) {}
}
