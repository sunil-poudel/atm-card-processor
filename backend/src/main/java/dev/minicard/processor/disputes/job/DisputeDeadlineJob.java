package dev.minicard.processor.disputes.job;

import dev.minicard.processor.disputes.repository.DisputeRepository;
import dev.minicard.processor.disputes.service.DisputeItemProcessor;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DisputeDeadlineJob {

  private static final Logger log = LoggerFactory.getLogger(DisputeDeadlineJob.class);
  private static final int BATCH_SIZE = 100;

  private final DisputeRepository disputeRepository;
  private final DisputeItemProcessor itemProcessor;
  private final Clock clock;

  public DisputeDeadlineJob(
      DisputeRepository disputeRepository, DisputeItemProcessor itemProcessor, Clock clock) {
    this.disputeRepository = disputeRepository;
    this.itemProcessor = itemProcessor;
    this.clock = clock;
  }

  @Scheduled(
      cron = "${app.disputes.deadline-job-cron:0 0 * * * *}",
      zone = "${app.business-zone:Asia/Kathmandu}")
  public int runOnce() {
    int totalProcessed = 0;
    while (true) {
      List<UUID> overdueIds = disputeRepository.findOverdueDisputeIds(clock.instant(), BATCH_SIZE);
      if (overdueIds.isEmpty()) {
        break;
      }

      int batchProcessed = 0;
      for (UUID id : overdueIds) {
        try {
          boolean handled = itemProcessor.processOverdueDispute(id);
          if (handled) {
            batchProcessed++;
            totalProcessed++;
          }
        } catch (Exception e) {
          log.error("Failed to process overdue dispute {}", id, e);
        }
      }

      if (batchProcessed == 0) {
        break;
      }
    }

    log.info("DisputeDeadlineJob run complete. Total disputes processed: {}", totalProcessed);
    return totalProcessed;
  }
}
