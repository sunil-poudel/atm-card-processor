package dev.minicard.processor.transactions.service;

import dev.minicard.processor.transactions.web.dto.DisputeSummaryResponse;
import java.util.Optional;
import java.util.UUID;

public interface TransactionDisputeLookup {

  Optional<DisputeSummaryResponse> findDisputeSummary(UUID transactionId);
}
