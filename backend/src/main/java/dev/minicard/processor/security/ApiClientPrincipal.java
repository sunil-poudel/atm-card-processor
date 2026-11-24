package dev.minicard.processor.security;

import java.util.UUID;

public record ApiClientPrincipal(UUID id, String name, String keyPrefix)
    implements java.io.Serializable {
  private static final long serialVersionUID = 1L;
}
