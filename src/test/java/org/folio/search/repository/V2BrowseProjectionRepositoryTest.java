package org.folio.search.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.client.RequestOptions.DEFAULT;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.client.RestHighLevelClient;

@UnitTest
@ExtendWith(MockitoExtension.class)
class V2BrowseProjectionRepositoryTest {

  @Mock
  private RestHighLevelClient client;
  @Mock
  private SearchRepository searchRepository;
  @Mock
  private JsonConverter jsonConverter;
  @Mock
  private BulkResponse bulkResponse;

  @InjectMocks
  private V2BrowseProjectionRepository repository;

  @BeforeEach
  void setUp() throws Exception {
    when(jsonConverter.toJson(any())).thenReturn("{}");
    when(client.bulk(any(BulkRequest.class), eq(DEFAULT))).thenReturn(bulkResponse);
    when(bulkResponse.hasFailures()).thenReturn(false);
  }

  @Test
  void bulkUpsert_splitsLargeRequestsIntoFixedSizeChunks() throws Exception {
    var docs = IntStream.range(0, 1001)
      .mapToObj(i -> Map.<String, Object>of("id", "id-" + i))
      .toList();
    var requestCaptor = ArgumentCaptor.forClass(BulkRequest.class);

    repository.bulkUpsert("browse-index", docs);

    verify(client, times(2)).bulk(requestCaptor.capture(), eq(DEFAULT));
    assertThat(requestCaptor.getAllValues())
      .extracting(BulkRequest::numberOfActions)
      .containsExactly(1000, 1);
  }

  @Test
  void bulkDelete_splitsLargeRequestsIntoFixedSizeChunks() throws Exception {
    var browseIds = IntStream.range(0, 1001)
      .mapToObj(i -> "id-" + i)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    var requestCaptor = ArgumentCaptor.forClass(BulkRequest.class);

    repository.bulkDelete("browse-index", browseIds);

    verify(client, times(2)).bulk(requestCaptor.capture(), eq(DEFAULT));
    assertThat(requestCaptor.getAllValues())
      .extracting(BulkRequest::numberOfActions)
      .containsExactly(1000, 1);
  }
}
