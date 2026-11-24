package dev.minicard.processor.disputes.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.minicard.processor.disputes.repository.DisputeRepository;
import dev.minicard.processor.disputes.service.DisputeItemProcessor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DisputeDeadlineJobTest {

  @Mock private DisputeRepository disputeRepository;
  @Mock private DisputeItemProcessor itemProcessor;

  private Clock clock;
  private DisputeDeadlineJob job;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC);
    job = new DisputeDeadlineJob(disputeRepository, itemProcessor, clock);
  }

  @Test
  @DisplayName("Batch of 250 items processed in batches of 100")
  void batchProcessingOf250Items() {
    List<UUID> batch1 = new ArrayList<>();
    for (int i = 0; i < 100; i++) batch1.add(UUID.randomUUID());

    List<UUID> batch2 = new ArrayList<>();
    for (int i = 0; i < 100; i++) batch2.add(UUID.randomUUID());

    List<UUID> batch3 = new ArrayList<>();
    for (int i = 0; i < 50; i++) batch3.add(UUID.randomUUID());

    when(disputeRepository.findOverdueDisputeIds(any(Instant.class), eq(100)))
        .thenReturn(batch1)
        .thenReturn(batch2)
        .thenReturn(batch3)
        .thenReturn(Collections.emptyList());

    when(itemProcessor.processOverdueDispute(any())).thenReturn(true);

    int total = job.runOnce();

    assertThat(total).isEqualTo(250);
  }

  @Test
  @DisplayName("Failing item does not prevent other items from being processed")
  void partialFailureIsolation() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();

    when(disputeRepository.findOverdueDisputeIds(any(Instant.class), anyInt()))
        .thenReturn(List.of(id1, id2))
        .thenReturn(Collections.emptyList());

    when(itemProcessor.processOverdueDispute(id1)).thenThrow(new RuntimeException("DB glitch"));
    when(itemProcessor.processOverdueDispute(id2)).thenReturn(true);

    int total = job.runOnce();

    assertThat(total).isEqualTo(1);
    verify(itemProcessor).processOverdueDispute(id1);
    verify(itemProcessor).processOverdueDispute(id2);
  }

  @Test
  @DisplayName("Second run when no overdue disputes exist is a no-op")
  void emptyRunProcessesZero() {
    when(disputeRepository.findOverdueDisputeIds(any(Instant.class), anyInt()))
        .thenReturn(Collections.emptyList());

    int total = job.runOnce();

    assertThat(total).isEqualTo(0);
  }
}
