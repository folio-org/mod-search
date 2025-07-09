package org.folio.search.service.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.utils.JsonTestUtils.OBJECT_MAPPER;
import static org.folio.support.utils.TestUtils.randomId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.search.sort.SortBuilders.fieldSort;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.util.List;
import java.util.function.Consumer;
import org.folio.search.configuration.properties.StreamIdsProperties;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.model.ResourceId;
import org.folio.search.model.ResourceIds;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.model.streamids.ResourceIdsJobEntity;
import org.folio.search.model.types.EntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.model.types.StreamJobStatus;
import org.folio.search.repository.ResourceIdsJobRepository;
import org.folio.search.repository.ResourceIdsTemporaryRepository;
import org.folio.search.repository.SearchRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.jdbc.core.RowCallbackHandler;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ResourceIdServiceTest {

  @Spy
  private final ObjectMapper objectMapper = OBJECT_MAPPER;
  @Spy
  @InjectMocks
  private ResourceIdService resourceIdService;
  @Mock
  private SearchRepository searchRepository;
  @Mock
  private CqlSearchQueryConverter queryConverter;
  @Mock
  private StreamIdsProperties properties;
  @Mock
  private ResourceIdsJobRepository jobRepository;
  @Mock
  private ResourceIdsTemporaryRepository idsTemporaryRepository;

  @Test
  void streamIdsFromDatabaseAsJson_positive() throws IOException {
    // Arrange
    final var jobId = randomId();
    final var temporaryTableName = "temp_table";
    final var resourceId = randomId();
    var job = new ResourceIdsJobEntity();
    job.setStatus(StreamJobStatus.COMPLETED);
    job.setQuery("query");
    job.setTemporaryTableName(temporaryTableName);

    when(jobRepository.getReferenceById(jobId)).thenReturn(job);
    doAnswer(invocation -> {
      var resultSetConsumer = invocation.<RowCallbackHandler>getArgument(1);
      var resultSet = mock(ResultSet.class);
      when(resultSet.getString(1)).thenReturn(resourceId);
      resultSetConsumer.processRow(resultSet);
      return null;
    }).when(idsTemporaryRepository).streamIds(eq(temporaryTableName), any());

    var outputStream = new ByteArrayOutputStream();

    // Act
    resourceIdService.streamResourceIdsAsJson(jobId, outputStream);

    // Assert
    var actual = objectMapper.readValue(outputStream.toByteArray(), ResourceIds.class);
    assertThat(actual).isEqualTo(new ResourceIds().ids(List.of(new ResourceId().id(resourceId))).totalRecords(1));
    verify(jobRepository).save(argThat(savedJob -> savedJob.getStatus() == StreamJobStatus.DEPRECATED));
    verify(idsTemporaryRepository).dropTableForIds(temporaryTableName);
  }

  @ParameterizedTest
  @EnumSource(value = StreamJobStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "COMPLETED")
  void streamResourceIdsAsJson_negative_whenNotCompletedJob(StreamJobStatus streamJobStatus) {
    // Arrange
    var resourceIdsJob = new ResourceIdsJobEntity();
    resourceIdsJob.setQuery("query");
    resourceIdsJob.setStatus(streamJobStatus);
    when(jobRepository.getReferenceById(any())).thenReturn(resourceIdsJob);
    var outputStream = new ByteArrayOutputStream();

    // Act & Assert
    assertThatThrownBy(() -> resourceIdService.streamResourceIdsAsJson(randomId(), outputStream))
      .hasMessage("Completed async job with query=[query] was not found.");
  }

  @ParameterizedTest
  @EnumSource(EntityType.class)
  void processResourceIdsJob_success(EntityType entityType) {
    // Arrange
    var idsJob = prepareJob(entityType);
    var searchSource = SearchSourceBuilder.searchSource();

    when(queryConverter.convertForConsortia(idsJob.getQuery(),
      entityType == EntityType.AUTHORITY ? ResourceType.AUTHORITY : ResourceType.INSTANCE, TENANT_ID))
      .thenReturn(searchSource);
    when(properties.getScrollQuerySize()).thenReturn(100);
    doAnswer(invocation -> {
      invocation.<Consumer<List<String>>>getArgument(2).accept(List.of("id1", "id2"));
      return null;
    }).when(searchRepository).streamResourceIds(any(), any(), any());

    // Act
    resourceIdService.processResourceIdsJob(idsJob, TENANT_ID);

    // Assert
    verify(idsTemporaryRepository).createTableForIds(idsJob.getTemporaryTableName());
    verify(searchRepository).streamResourceIds(any(CqlResourceIdsRequest.class), eq(searchSource.size(100)
      .fetchSource(new String[] {entityType.getSourceIdPath()}, null)
      .sort(fieldSort("_doc"))), any());
    verify(idsTemporaryRepository).insertIds(List.of("id1", "id2"), idsJob.getTemporaryTableName());
    idsJob.setStatus(StreamJobStatus.COMPLETED);
    verify(jobRepository).save(idsJob);
  }

  @Test
  void processResourceIdsJob_negative() {
    // Arrange
    var job = prepareJob(EntityType.AUTHORITY);
    var tableName = job.getTemporaryTableName();

    doThrow(new RuntimeException("Test exception"))
      .when(idsTemporaryRepository).createTableForIds(tableName);

    // Act
    resourceIdService.processResourceIdsJob(job, TENANT_ID);

    // Assert
    verify(idsTemporaryRepository).createTableForIds(tableName);
    verify(idsTemporaryRepository).dropTableForIds(tableName);
    job.setStatus(StreamJobStatus.ERROR);
    verify(jobRepository).save(job);
  }

  private ResourceIdsJobEntity prepareJob(EntityType entityType) {
    var resourceIdsJob = new ResourceIdsJobEntity();
    resourceIdsJob.setEntityType(entityType);
    resourceIdsJob.setTemporaryTableName("temp_table");
    resourceIdsJob.setQuery("id=*");
    return resourceIdsJob;
  }
}
