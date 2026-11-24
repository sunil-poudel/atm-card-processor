package dev.minicard.processor.transactions.service;

import dev.minicard.processor.transactions.web.dto.AuthorizeResponse;

public record AuthorizeExecutionResult(AuthorizeResponse response, boolean replayed) {}
