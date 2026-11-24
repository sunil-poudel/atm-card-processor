package dev.minicard.processor.common.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

  @Test
  void generatesValidUuidV7() {
    UUID uuid = UuidV7.newId();

    assertThat(uuid).isNotNull();
    assertThat(uuid.version()).isEqualTo(7);
    assertThat(uuid.variant()).isEqualTo(2); // RFC 4122/9562 variant
  }

  @Test
  void encodesTimestampCorrectly() {
    long expectedEpochMillis = 1776739200000L; // fixed timestamp
    Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(expectedEpochMillis), ZoneOffset.UTC);

    UUID uuid = UuidV7.newId(fixedClock);

    assertThat(uuid.version()).isEqualTo(7);
    long extractedTimestamp = uuid.getMostSignificantBits() >>> 16;
    assertThat(extractedTimestamp).isEqualTo(expectedEpochMillis);
  }

  @Test
  void generatedIdsAreTimeOrdered() {
    Clock clock1 = Clock.fixed(Instant.ofEpochMilli(1000L), ZoneOffset.UTC);
    Clock clock2 = Clock.fixed(Instant.ofEpochMilli(2000L), ZoneOffset.UTC);

    UUID uuid1 = UuidV7.newId(clock1);
    UUID uuid2 = UuidV7.newId(clock2);

    assertThat(uuid1.toString()).isLessThan(uuid2.toString());
  }
}
