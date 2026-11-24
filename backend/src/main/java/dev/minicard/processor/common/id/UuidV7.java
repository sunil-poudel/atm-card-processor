package dev.minicard.processor.common.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

public final class UuidV7 {

  private static final SecureRandom RANDOM = new SecureRandom();

  private UuidV7() {}

  public static UUID newId() {
    return generate(System.currentTimeMillis(), RANDOM);
  }

  public static UUID newId(Clock clock) {
    return generate(clock.millis(), RANDOM);
  }

  public static UUID generate(long timestampMillis, SecureRandom random) {
    byte[] randomBytes = new byte[10];
    random.nextBytes(randomBytes);

    long mostSignificant = (timestampMillis << 16) & 0xFFFFFFFFFFFF0000L;
    int randA = ((randomBytes[0] & 0xFF) << 4) | ((randomBytes[1] & 0xF0) >>> 4);
    mostSignificant |= 0x0000000000007000L; // version 7
    mostSignificant |= (randA & 0x0FFFL);

    long leastSignificant =
        ((long) (randomBytes[1] & 0x0F) << 56)
            | ((long) (randomBytes[2] & 0xFF) << 48)
            | ((long) (randomBytes[3] & 0xFF) << 40)
            | ((long) (randomBytes[4] & 0xFF) << 32)
            | ((long) (randomBytes[5] & 0xFF) << 24)
            | ((long) (randomBytes[6] & 0xFF) << 16)
            | ((long) (randomBytes[7] & 0xFF) << 8)
            | ((long) (randomBytes[8] & 0xFF));

    leastSignificant &= 0x3FFFFFFFFFFFFFFFL;
    leastSignificant |= 0x8000000000000000L; // variant 1 (RFC 4122 / 9562)

    return new UUID(mostSignificant, leastSignificant);
  }
}
