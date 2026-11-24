package dev.minicard.processor.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BusinessCalendarTest {

  private static final ZoneId KATHMANDU_ZONE = ZoneId.of("Asia/Kathmandu");
  private BusinessCalendar calendar;
  private Clock fixedClock;

  @BeforeEach
  void setUp() {
    // 2026-10-21 12:00:00 in Kathmandu is 2026-10-21 06:15:00 UTC (+05:45)
    Instant now = Instant.parse("2026-10-21T06:15:00Z");
    fixedClock = Clock.fixed(now, KATHMANDU_ZONE);
    calendar = new BusinessCalendar(KATHMANDU_ZONE, fixedClock);
  }

  @Test
  void localDateConvertsUtcInstantToKathmanduDate() {
    // 2026-10-20 18:14:59Z is 23:59:59 on Oct 20 in Kathmandu
    Instant lateNightUtc = Instant.parse("2026-10-20T18:14:59Z");
    assertThat(calendar.toLocalDate(lateNightUtc)).isEqualTo(LocalDate.of(2026, 10, 20));

    // 2026-10-20 18:15:00Z is 00:00:00 on Oct 21 in Kathmandu
    Instant midnightUtc = Instant.parse("2026-10-20T18:15:00Z");
    assertThat(calendar.toLocalDate(midnightUtc)).isEqualTo(LocalDate.of(2026, 10, 21));
  }

  @Test
  void startOfDayComputesExactUtcInstantForKathmanduMidnight() {
    LocalDate date = LocalDate.of(2026, 10, 21);
    Instant start = calendar.startOfDay(date);

    // Midnight 2026-10-21 in +05:45 is 2026-10-20 18:15:00 UTC
    assertThat(start).isEqualTo(Instant.parse("2026-10-20T18:15:00Z"));
  }

  @Test
  void endOfDayExclusiveComputesNextDayMidnightUtc() {
    LocalDate date = LocalDate.of(2026, 10, 21);
    Instant end = calendar.endOfDayExclusive(date);

    // Midnight 2026-10-22 in +05:45 is 2026-10-21 18:15:00 UTC
    assertThat(end).isEqualTo(Instant.parse("2026-10-21T18:15:00Z"));
  }

  @Test
  void dayRangeContainsInstantsWithinLocalDay() {
    LocalDate date = LocalDate.of(2026, 10, 21);
    BusinessCalendar.DayRange range = calendar.dayRange(date);

    assertThat(range.startInclusive()).isEqualTo(Instant.parse("2026-10-20T18:15:00Z"));
    assertThat(range.endExclusive()).isEqualTo(Instant.parse("2026-10-21T18:15:00Z"));

    assertThat(range.contains(Instant.parse("2026-10-20T18:14:59Z"))).isFalse();
    assertThat(range.contains(Instant.parse("2026-10-20T18:15:00Z"))).isTrue();
    assertThat(range.contains(Instant.parse("2026-10-21T12:00:00Z"))).isTrue();
    assertThat(range.contains(Instant.parse("2026-10-21T18:14:59.999Z"))).isTrue();
    assertThat(range.contains(Instant.parse("2026-10-21T18:15:00Z"))).isFalse();
  }

  @Test
  void todayReturnsLocalCalendarDate() {
    assertThat(calendar.today()).isEqualTo(LocalDate.of(2026, 10, 21));
  }
}
