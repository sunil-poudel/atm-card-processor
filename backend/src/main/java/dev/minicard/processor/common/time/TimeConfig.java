package dev.minicard.processor.common.time;

import dev.minicard.processor.common.config.AppProperties;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

  @Bean
  @ConditionalOnMissingBean(Clock.class)
  public Clock clock(AppProperties appProperties) {
    return Clock.system(appProperties.businessZone());
  }

  @Bean
  public BusinessCalendar businessCalendar(AppProperties appProperties, Clock clock) {
    return new BusinessCalendar(appProperties.businessZone(), clock);
  }
}
