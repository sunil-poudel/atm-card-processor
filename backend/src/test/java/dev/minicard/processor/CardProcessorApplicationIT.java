package dev.minicard.processor;

import dev.minicard.processor.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CardProcessorApplicationIT extends PostgresContainerSupport {

  @Test
  void contextLoads() {}
}
