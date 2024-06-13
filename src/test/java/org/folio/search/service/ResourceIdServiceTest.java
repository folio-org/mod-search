package org.folio.search.service;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.model.service.CqlResourceIdsRequest.INSTANCE_ID_PATH;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.randomId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import org.folio.search.configuration.properties.StreamIdsProperties;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.ResourceId;
import org.folio.search.model.ResourceIds;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.model.streamids.ResourceIdsJobEntity;
import org.folio.search.model.types.StreamJobStatus;
import org.folio.search.repository.ResourceIdsJobRepository;
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

@UnitTest
@ExtendWith(MockitoExtension.class)
class ResourceIdServiceTest {

  private static final String RANDOM_ID = randomId();
  private static final String TEST_QUERY = "id==" + RANDOM_ID;
  private static final Integer QUERY_SIZE = 1000;

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
  @Spy
  private final ObjectMapper objectMapper = OBJECT_MAPPER;

  @Test
  void streamResourceIds() throws IOException {
    when(queryConverter.convertForConsortia(TEST_QUERY, RESOURCE_NAME, TENANT_ID)).thenReturn(searchSource());
    when(properties.getScrollQuerySize()).thenReturn(QUERY_SIZE);
    mockSearchRepositoryCall(List.of(RANDOM_ID));

    var outputStream = new ByteArrayOutputStream();

    resourceIdService.streamResourceIdsAsJson(request(), outputStream);

    var actual = objectMapper.readValue(outputStream.toByteArray(), ResourceIds.class);
    assertThat(actual).isEqualTo(new ResourceIds().ids(List.of(new ResourceId().id(RANDOM_ID))).totalRecords(1));
  }

  @Test
  void streamResourceIdsAsText() {
    when(queryConverter.convertForConsortia(TEST_QUERY, RESOURCE_NAME, TENANT_ID)).thenReturn(searchSource());
    when(properties.getScrollQuerySize()).thenReturn(QUERY_SIZE);
    mockSearchRepositoryCall(List.of(RANDOM_ID));

    var outputStream = new ByteArrayOutputStream();

    resourceIdService.streamResourceIdsAsText(request(), outputStream);

    var actual = outputStream.toString();
    assertThat(actual).isEqualTo(RANDOM_ID + '\n');
  }

  @EnumSource(value = StreamJobStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "COMPLETED")
  @ParameterizedTest
  void cantStreamNotCompletedJob(StreamJobStatus streamJobStatus) {
    var resourceIdsJob = new ResourceIdsJobEntity();
    resourceIdsJob.setQuery("query");
    resourceIdsJob.setStatus(streamJobStatus);
    when(jobRepository.getReferenceById(any())).thenReturn(resourceIdsJob);
    var outputStream = new ByteArrayOutputStream();

    assertThatThrownBy(() -> resourceIdService.streamIdsFromDatabaseAsJson(randomId(), outputStream))
      .hasMessage("Completed async job with query=[query] was not found.");
  }

  @Test
  void streamResourceIds_negative_throwException() throws IOException {
    var outputStream = new ByteArrayOutputStream();

    when(objectMapper.createGenerator(outputStream)).thenThrow(new IOException("Failed to create generator"));

    var request = request();
    assertThatThrownBy(() -> resourceIdService.streamResourceIdsAsJson(request, outputStream))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage("Failed to write data into json [reason: Failed to create generator]");
  }

  @Test
  void streamResourceIds_negative_throwExceptionOnWritingIdField() throws IOException {
    var outputStream = new ByteArrayOutputStream();
    var generator = spy(objectMapper.createGenerator(outputStream));

    mockSearchRepositoryCall(List.of(RANDOM_ID));
    when(properties.getScrollQuerySize()).thenReturn(QUERY_SIZE);
    when(objectMapper.createGenerator(outputStream)).thenReturn(generator);
    when(queryConverter.convertForConsortia(TEST_QUERY, RESOURCE_NAME, TENANT_ID)).thenReturn(searchSource());
    doThrow(new IOException("Failed to write string field")).when(generator).writeStringField("id", RANDOM_ID);

    var request = request();
    assertThatThrownBy(() -> resourceIdService.streamResourceIdsAsJson(request, outputStream))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage("Failed to write to id value into json stream [reason: Failed to write string field]");
  }

  @Test
  void streamResourceIdsAsText_negative_throwExceptionOnWritingIdField() throws IOException {
    var outputStream = new ByteArrayOutputStream();
    var writer = spy(resourceIdService.createOutputStreamWriter(outputStream));

    mockSearchRepositoryCall(List.of(RANDOM_ID));
    when(properties.getScrollQuerySize()).thenReturn(QUERY_SIZE);
    when(resourceIdService.createOutputStreamWriter(outputStream)).thenReturn(writer);
    when(queryConverter.convertForConsortia(TEST_QUERY, RESOURCE_NAME, TENANT_ID)).thenReturn(searchSource());
    doThrow(new IOException("Failed to write string field")).when(writer).write(RANDOM_ID + '\n');

    var request = request();
    assertThatThrownBy(() -> resourceIdService.streamResourceIdsAsText(request, outputStream))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage("Failed to write id value into output stream [reason: Failed to write string field]");
  }

  @Test
  void streamResourceIds_positive_emptyCollectionProvided() throws IOException {
    mockSearchRepositoryCall(emptyList());
    when(queryConverter.convertForConsortia(TEST_QUERY, RESOURCE_NAME, TENANT_ID)).thenReturn(searchSource());
    when(properties.getScrollQuerySize()).thenReturn(QUERY_SIZE);

    var outputStream = new ByteArrayOutputStream();
    resourceIdService.streamResourceIdsAsJson(request(), outputStream);

    var actual = objectMapper.readValue(outputStream.toByteArray(), ResourceIds.class);
    assertThat(actual).isEqualTo(new ResourceIds().ids(emptyList()).totalRecords(0));
  }

  @Test
  void streamResourceIdsInTextTextType_positive_emptyCollectionProvided() {
    mockSearchRepositoryCall(emptyList());
    when(queryConverter.convertForConsortia(TEST_QUERY, RESOURCE_NAME, TENANT_ID)).thenReturn(searchSource());
    when(properties.getScrollQuerySize()).thenReturn(QUERY_SIZE);

    var outputStream = new ByteArrayOutputStream();
    resourceIdService.streamResourceIdsAsText(request(), outputStream);

    var actual = outputStream.toString();
    assertThat(actual).isEmpty();
  }

  private void mockSearchRepositoryCall(List<String> ids) {
    var expectedSearchSource = searchSource().size(QUERY_SIZE).sort("_doc");
    doAnswer(invocation -> {
      invocation.<Consumer<List<String>>>getArgument(2).accept(ids);
      return null;
    }).when(searchRepository).streamResourceIds(eq(request()), eq(expectedSearchSource), any());
  }

  private static CqlResourceIdsRequest request() {
    return CqlResourceIdsRequest.of(RESOURCE_NAME, TENANT_ID, TEST_QUERY, INSTANCE_ID_PATH);
  }

  private static SearchSourceBuilder searchSource() {
    return SearchSourceBuilder.searchSource()
      .query(termQuery("id", RANDOM_ID))
      .fetchSource(new String[] {"id"}, null);
  }
}
