package dev.minicard.processor.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

public class BusinessCalendar {

  private final ZoneId businessZone;
  private final Clock clock;

  public BusinessCalendar(ZoneId businessZone, Clock clock) {
    this.businessZone = Objects.requireNonNull(businessZone, "businessZone cannot be null");
    this.clock = Objects.requireNonNull(clock, "clock cannot be null");
  }

  public ZoneId businessZone() {
    return businessZone;
  }

  public LocalDate today() {
    return toLocalDate(clock.instant());
  }

  public LocalDate toLocalDate(Instant instant) {
    return instant.atZone(businessZone).toLocalDate();
  }

  public Instant startOfDay(LocalDate date) {
    return date.atStartOfDay(businessZone).toInstant();
  }

  public Instant endOfDayExclusive(LocalDate date) {
    return date.plusDays(1).atStartOfDay(businessZone).toInstant();
  }

  public DayRange dayRange(LocalDate date) {
    return new DayRange(startOfDay(date), endOfDayExclusive(date));
  }

  public DayRange dayRange(Instant instant) {
    return dayRange(toLocalDate(instant));
  }

  public DayRange currentDayRange() {
    return dayRange(clock.instant());
  }

  public record DayRange(Instant startInclusive, Instant endExclusive) {
    public boolean contains(Instant instant) {
      return !instant.isBefore(startInclusive) && instant.isBefore(endExclusive);
    }
  }
}
