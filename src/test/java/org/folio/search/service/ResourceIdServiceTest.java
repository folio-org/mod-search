package org.folio.search.service;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.folio.search.utils.SearchUtils.MAX_ELASTICSEARCH_QUERY_SIZE;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.domain.dto.ResourceId;
import org.folio.search.domain.dto.ResourceIds;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ResourceIdServiceTest {

  private static final String RANDOM_ID = randomId();
  private static final String TEST_QUERY = "id==" + RANDOM_ID;

  @InjectMocks private ResourceIdService resourceIdService;
  @Mock private SearchRepository searchRepository;
  @Mock private CqlSearchQueryConverter queryConverter;
  @Spy private final ObjectMapper objectMapper = OBJECT_MAPPER;

  @Test
  void streamResourceIds() throws IOException {
    var request = request();
    var outputStream = new ByteArrayOutputStream();

    when(queryConverter.convert(request.getQuery(), RESOURCE_NAME)).thenReturn(searchSource());
    mockSearchRepositoryCall(List.of(RANDOM_ID));

    resourceIdService.streamResourceIds(request, outputStream);

    var actual = objectMapper.readValue(outputStream.toByteArray(), ResourceIds.class);
    assertThat(actual).isEqualTo(new ResourceIds().ids(List.of(new ResourceId().id(RANDOM_ID))).totalRecords(1));
  }

  @Test
  void streamResourceIds_negative_throwException() throws IOException {
    var request = request();
    var outputStream = new ByteArrayOutputStream();

    when(objectMapper.createGenerator(outputStream)).thenThrow(new IOException("Failed to create generator"));

    assertThatThrownBy(() -> resourceIdService.streamResourceIds(request, outputStream))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage("Failed to write data into json [reason: Failed to create generator]");
  }

  @Test
  void streamResourceIds_negative_throwExceptionOnWritingIdField() throws IOException {
    var request = request();
    var outputStream = new ByteArrayOutputStream();
    var generator = spy(objectMapper.createGenerator(outputStream));

    mockSearchRepositoryCall(List.of(RANDOM_ID));
    when(queryConverter.convert(request.getQuery(), RESOURCE_NAME)).thenReturn(searchSource());
    when(objectMapper.createGenerator(outputStream)).thenReturn(generator);
    doThrow(new IOException("Failed to write string field")).when(generator).writeStringField("id", RANDOM_ID);

    assertThatThrownBy(() -> resourceIdService.streamResourceIds(request, outputStream))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage("Failed to write to id value into json stream [reason: Failed to write string field]");
  }

  @Test
  void streamResourceIds_positive_emptyCollectionProvided() throws IOException {
    var request = request();
    var outputStream = new ByteArrayOutputStream();

    mockSearchRepositoryCall(emptyList());
    when(queryConverter.convert(request.getQuery(), RESOURCE_NAME)).thenReturn(searchSource());

    resourceIdService.streamResourceIds(request, outputStream);

    var actual = objectMapper.readValue(outputStream.toByteArray(), ResourceIds.class);
    assertThat(actual).isEqualTo(new ResourceIds().ids(emptyList()).totalRecords(0));
  }

  private void mockSearchRepositoryCall(List<String> ids) {
    var expectedSearchSource = searchSource().size(MAX_ELASTICSEARCH_QUERY_SIZE).sort("_doc");
    doAnswer(invocation -> {
      invocation.<Consumer<List<String>>>getArgument(2).accept(ids);
      return null;
    }).when(searchRepository).streamResourceIds(eq(request()), eq(expectedSearchSource), any());
  }

  private static CqlResourceIdsRequest request() {
    return CqlResourceIdsRequest.of(TEST_QUERY, RESOURCE_NAME, TENANT_ID);
  }

  private static SearchSourceBuilder searchSource() {
    return SearchSourceBuilder.searchSource().query(termQuery("id", RANDOM_ID));
  }
}
