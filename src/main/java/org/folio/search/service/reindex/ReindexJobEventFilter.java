package org.folio.search.service.reindex;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.configuration.properties.ReindexConfigurationProperties.MissingJobIdPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decides whether a reindex Kafka event belongs to the reindex job initiated by this mod-search instance.
 *
 * <p>Several mod-search instances (e.g. a live v1 and a background v2) consume the same shared reindex topics with
 * separate consumer groups, so each of them receives every event. Only events whose job identifier matches the
 * active job of the tenant are processed; all others are skipped before any reindex state, PostgreSQL table or
 * OpenSearch index is touched.
 *
 * <p>Must be invoked within the execution context of the tenant that owns the reindex (the central tenant for
 * consortia), since the active job is resolved from that tenant's schema.
 */
@Log4j2
@Component
public class ReindexJobEventFilter {

  public static final String METRIC_NAME = "search.reindex.events";

  private final ReindexJobService jobService;
  private final ReindexConfigurationProperties reindexConfig;
  private final MeterRegistry meterRegistry;
  private final String moduleName;

  public ReindexJobEventFilter(ReindexJobService jobService,
                               ReindexConfigurationProperties reindexConfig,
                               MeterRegistry meterRegistry,
                               @Value("${spring.application.name}") String moduleName) {
    this.jobService = jobService;
    this.reindexConfig = reindexConfig;
    this.meterRegistry = meterRegistry;
    this.moduleName = moduleName;
  }

  /**
   * Evaluates the event and records the outcome in logs and metrics.
   *
   * @return true if the event must be processed, false if it must be skipped
   */
  public boolean shouldProcess(ReindexEventInfo event) {
    var decision = decide(event);
    record(event, decision);
    return decision.process();
  }

  private Decision decide(ReindexEventInfo event) {
    var filterConfig = reindexConfig.getJobFilter();
    if (!filterConfig.isEnabled()) {
      return Decision.FILTER_DISABLED;
    }

    if (isBlank(event.jobId())) {
      return filterConfig.getMissingJobIdPolicy() == MissingJobIdPolicy.PROCESS
             ? Decision.MISSING_JOB_ID_PROCESSED
             : Decision.MISSING_JOB_ID_IGNORED;
    }

    var eventJobId = parseUuid(event.jobId());
    if (eventJobId.isEmpty()) {
      return Decision.MALFORMED_JOB_ID;
    }

    return jobService.getActiveJobId()
      .map(activeJobId -> activeJobId.equals(eventJobId.get()) ? Decision.MATCHED : Decision.JOB_ID_MISMATCH)
      .orElse(Decision.NO_ACTIVE_JOB);
  }

  private void record(ReindexEventInfo event, Decision decision) {
    Counter.builder(METRIC_NAME)
      .description("Reindex Kafka events by job-id filtering outcome")
      .tag("eventType", event.eventType())
      .tag("outcome", decision.outcome)
      .tag("reason", decision.reason)
      .register(meterRegistry)
      .increment();

    var level = switch (decision) {
      case MATCHED, FILTER_DISABLED, MISSING_JOB_ID_PROCESSED -> Level.DEBUG;
      case MALFORMED_JOB_ID -> Level.WARN;
      default -> Level.INFO;
    };
    log.log(level, "shouldProcess:: reindex event {} [reason: {}, module: {}, tenant: {}, eventType: {}, "
                   + "rangeId: {}, reindexJobId: {}, jobIdSource: {}]",
      decision.outcome, decision.reason, moduleName, event.tenantId(), event.eventType(), event.rangeId(),
      event.jobId(), event.jobIdSource());
  }

  private static Optional<UUID> parseUuid(String value) {
    try {
      return Optional.of(UUID.fromString(value.trim()));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /**
   * Metadata of a received reindex event needed for the filtering decision and diagnostics.
   *
   * @param eventType   reindex event type, e.g. {@code reindex-records}
   * @param tenantId    tenant the event was published for
   * @param rangeId     merge/upload range identifier, if known
   * @param jobId       raw reindex job identifier carried by the event, may be null
   * @param jobIdSource where the identifier was read from (header or payload field), for diagnostics
   */
  public record ReindexEventInfo(String eventType, String tenantId, String rangeId, String jobId,
                                 String jobIdSource) { }

  private enum Decision {
    MATCHED(true, "processed", "job_id_matched"),
    FILTER_DISABLED(true, "processed", "filter_disabled"),
    MISSING_JOB_ID_PROCESSED(true, "processed", "missing_job_id_fallback"),
    MISSING_JOB_ID_IGNORED(false, "ignored", "missing_job_id"),
    MALFORMED_JOB_ID(false, "invalid", "malformed_job_id"),
    NO_ACTIVE_JOB(false, "unmatched", "no_active_job"),
    JOB_ID_MISMATCH(false, "unmatched", "job_id_mismatch");

    private final boolean process;
    private final String outcome;
    private final String reason;

    Decision(boolean process, String outcome, String reason) {
      this.process = process;
      this.outcome = outcome;
      this.reason = reason;
    }

    boolean process() {
      return process;
    }
  }
}
