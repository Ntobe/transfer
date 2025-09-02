package com.example.transfer.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class CircuitBreakerLoggingConfig {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerLoggingConfig.class);

    @Bean
    public ApplicationRunner circuitBreakerEventLogger(
            CircuitBreakerRegistry registry) {
        return args -> {
            registry.getAllCircuitBreakers().forEach(cb -> {
                cb.getEventPublisher()
                        .onStateTransition(e -> log.warn("{}", Map.of(
                                "event", "circuit_breaker_state_change",
                                "name", e.getCircuitBreakerName(),
                                "fromState", e.getStateTransition().getFromState(),
                                "toState", e.getStateTransition().getToState(),
                                "failureRate", safeMetric(cb.getMetrics().getFailureRate()),
                                "slowCallRate", safeMetric(cb.getMetrics().getSlowCallRate())
                        )))
                        .onError(e -> log.error("{}", Map.of(
                                "event", "circuit_breaker_error",
                                "name", e.getCircuitBreakerName(),
                                "type", e.getEventType(),
                                "durationMs", e.getElapsedDuration().toMillis(),
                                "throwable", e.getThrowable() != null ? e.getThrowable().getClass().getSimpleName() : "none"
                        )));
            });
        };
    }

    private static String safeMetric(float v) {
        return Float.isNaN(v) ? "NaN" : String.format("%.2f", v);
    }
}

