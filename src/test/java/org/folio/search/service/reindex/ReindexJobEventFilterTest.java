package org.folio.search.service.reindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.service.reindex.ReindexJobEventFilter.METRIC_NAME;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.configuration.properties.ReindexConfigurationProperties.MissingJobIdPolicy;
import org.folio.search.service.reindex.ReindexJobEventFilter.ReindexEventInfo;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ReindexJobEventFilterTest {

  private static final String EVENT_TYPE = "reindex-records";
  private static final UUID ACTIVE_JOB_ID = UUID.randomUUID();

  @Mock
  private ReindexJobService jobService;

  private ReindexConfigurationProperties config;
  private SimpleMeterRegistry meterRegistry;
  private ReindexJobEventFilter filter;

  @BeforeEach
  void setUp() {
    config = new ReindexConfigurationProperties();
    meterRegistry = new SimpleMeterRegistry();
    filter = new ReindexJobEventFilter(jobService, config, meterRegistry, "mod-search-v2");
  }

  @Test
  void shouldProcess_positive_matchingJobId() {
    when(jobService.getActiveJobId()).thenReturn(Optional.of(ACTIVE_JOB_ID));

    assertThat(filter.shouldProcess(event(ACTIVE_JOB_ID.toString()))).isTrue();
    assertCounted("processed", "job_id_matched");
  }

  @Test
  void shouldProcess_negative_jobIdOfAnotherJob() {
    when(jobService.getActiveJobId()).thenReturn(Optional.of(ACTIVE_JOB_ID));

    assertThat(filter.shouldProcess(event(UUID.randomUUID().toString()))).isFalse();
    assertCounted("unmatched", "job_id_mismatch");
  }

  @Test
  void shouldProcess_negative_noActiveJobForTenant() {
    when(jobService.getActiveJobId()).thenReturn(Optional.empty());

    assertThat(filter.shouldProcess(event(UUID.randomUUID().toString()))).isFalse();
    assertCounted("unmatched", "no_active_job");
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-a-uuid", "12345", "' OR 1=1"})
  void shouldProcess_negative_malformedJobId(String jobId) {
    assertThat(filter.shouldProcess(event(jobId))).isFalse();
    assertCounted("invalid", "malformed_job_id");
    verifyNoInteractions(jobService);
  }

  @Test
  void shouldProcess_positive_missingJobIdWithProcessPolicy() {
    assertThat(filter.shouldProcess(event(null))).isTrue();
    assertCounted("processed", "missing_job_id_fallback");
    verifyNoInteractions(jobService);
  }

  @Test
  void shouldProcess_negative_missingJobIdWithIgnorePolicy() {
    config.getJobFilter().setMissingJobIdPolicy(MissingJobIdPolicy.IGNORE);

    assertThat(filter.shouldProcess(event(" "))).isFalse();
    assertCounted("ignored", "missing_job_id");
    verifyNoInteractions(jobService);
  }

  @Test
  void shouldProcess_positive_filterDisabled() {
    config.getJobFilter().setEnabled(false);

    assertThat(filter.shouldProcess(event(UUID.randomUUID().toString()))).isTrue();
    assertCounted("processed", "filter_disabled");
    verifyNoInteractions(jobService);
  }

  private static ReindexEventInfo event(String jobId) {
    return new ReindexEventInfo(EVENT_TYPE, TENANT_ID, UUID.randomUUID().toString(), jobId, "header");
  }

  private void assertCounted(String outcome, String reason) {
    var counter = meterRegistry.find(METRIC_NAME)
      .tags("eventType", EVENT_TYPE, "outcome", outcome, "reason", reason)
      .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }
}
