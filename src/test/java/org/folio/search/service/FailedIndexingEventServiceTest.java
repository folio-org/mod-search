package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.within;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.model.types.ResourceType.ITEM;
import static org.folio.support.TestConstants.RESOURCE_ID;
import static org.folio.support.utils.JsonTestUtils.OBJECT_MAPPER;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.resourceEvent;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.exception.IndexingFailureException;
import org.folio.search.model.entity.FailedIndexingEvent;
import org.folio.search.repository.FailedIndexingEventRepository;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.retry.RetryException;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FailedIndexingEventServiceTest {

  @Mock
  private FailedIndexingEventRepository repository;
  @Spy
  private final JsonConverter jsonConverter = new JsonConverter(OBJECT_MAPPER);
  @Spy
  private final SearchConfigurationProperties searchConfigurationProperties = searchConfigurationProperties();
  @InjectMocks
  private FailedIndexingEventService service;
  @Captor
  private ArgumentCaptor<FailedIndexingEvent> failedEventCaptor;
  @Captor
  private ArgumentCaptor<Timestamp> thresholdCaptor;

  @Test
  void save_positive_storesEventWithFailureReason() {
    var event = resourceEvent(RESOURCE_ID, ITEM, CREATE, mapOf("id", RESOURCE_ID), null);

    service.save(event, new IllegalStateException("index is read-only"));

    verify(repository).save(failedEventCaptor.capture());
    var failedEvent = failedEventCaptor.getValue();
    assertThat(failedEvent.id()).isNotNull();
    assertThat(failedEvent.resourceType()).isEqualTo(ITEM.getName());
    assertThat(failedEvent.resourceId()).isEqualTo(RESOURCE_ID);
    assertThat(failedEvent.eventType()).isEqualTo(CREATE.getValue());
    assertThat(failedEvent.event()).contains(RESOURCE_ID);
    assertThat(failedEvent.errorMessage()).isEqualTo("IllegalStateException: index is read-only");
    assertThat(failedEvent.status()).isNull();
    assertThat(failedEvent.errorType()).isNull();
    assertThat(failedEvent.createdDate()).isNotNull();
  }

  @Test
  void save_positive_storesOpenSearchFailureDetails() {
    var event = resourceEvent(RESOURCE_ID, ITEM, CREATE, mapOf("id", RESOURCE_ID), null);
    var failure = new RetryException("Retry policy exhausted",
      new IndexingFailureException("Failed to index item documents: status [400]", 400, "mapper_parsing_exception"));

    service.save(event, failure);

    verify(repository).save(failedEventCaptor.capture());
    var failedEvent = failedEventCaptor.getValue();
    assertThat(failedEvent.status()).isEqualTo(400);
    assertThat(failedEvent.errorType()).isEqualTo("mapper_parsing_exception");
    assertThat(failedEvent.errorMessage())
      .isEqualTo("IndexingFailureException: Failed to index item documents: status [400]");
  }

  @Test
  void save_negative_storageFailureIsNotPropagated() {
    var event = resourceEvent(RESOURCE_ID, ITEM, CREATE, mapOf("id", RESOURCE_ID), null);
    doThrow(new IllegalStateException("db is down")).when(repository).save(any());
    var exception = new IllegalStateException("index is read-only");

    assertThatNoException().isThrownBy(() -> service.save(event, exception));
  }

  @Test
  void deleteExpired_positive_usesRetentionPeriod() {
    when(repository.deleteOlderThan(thresholdCaptor.capture())).thenReturn(3);
    var expectedThreshold = Instant.now().minus(30, ChronoUnit.DAYS);

    var deleted = service.deleteExpired();

    assertThat(deleted).isEqualTo(3);
    assertThat(thresholdCaptor.getValue().toInstant())
      .isCloseTo(expectedThreshold, within(1, ChronoUnit.MINUTES));
  }

  private static SearchConfigurationProperties searchConfigurationProperties() {
    var properties = new SearchConfigurationProperties();
    var indexing = new SearchConfigurationProperties.IndexingSettings();
    indexing.setFailedEventsRetentionDays(30);
    properties.setIndexing(indexing);
    return properties;
  }
}
