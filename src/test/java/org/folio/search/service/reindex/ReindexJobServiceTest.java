package org.folio.search.service.reindex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.folio.search.service.reindex.jdbc.ReindexJobRepository;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ReindexJobServiceTest {

  @Mock
  private ReindexJobRepository jobRepository;
  @Mock
  private FolioExecutionContext context;
  @InjectMocks
  private ReindexJobService jobService;

  @Test
  void startNewJob_registersUniqueJobIds() {
    var first = jobService.startNewJob();
    var second = jobService.startNewJob();

    assertThat(first).isNotEqualTo(second);
    verify(jobRepository).replaceActiveJob(first);
    verify(jobRepository).replaceActiveJob(second);
  }

  @Test
  void getOrStartJob_reusesActiveJob() {
    var activeJobId = UUID.randomUUID();
    when(jobRepository.findActiveJobId()).thenReturn(Optional.of(activeJobId));

    assertThat(jobService.getOrStartJob()).isEqualTo(activeJobId);
    verify(jobRepository, never()).replaceActiveJob(any());
  }

  @Test
  void getOrStartJob_registersJobWhenNoneActive() {
    when(jobRepository.findActiveJobId()).thenReturn(Optional.empty());

    var jobId = jobService.getOrStartJob();

    assertThat(jobId).isNotNull();
    verify(jobRepository).replaceActiveJob(jobId);
  }
}
