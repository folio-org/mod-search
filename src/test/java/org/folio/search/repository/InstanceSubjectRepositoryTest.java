package org.folio.search.repository;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.model.types.IndexingDataFormat.SMILE;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.SMILE_MAPPER;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.spyLambda;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.client.RequestOptions.DEFAULT;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.SneakyThrows;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.SmileConverter;
import org.folio.spring.test.type.UnitTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.script.Script;

@UnitTest
@ExtendWith(MockitoExtension.class)
class InstanceSubjectRepositoryTest {

  @InjectMocks
  private InstanceSubjectRepository repository;

  @Spy
  private JsonConverter jsonConverter = new JsonConverter(OBJECT_MAPPER);
  @Spy
  private SmileConverter smileConverter = new SmileConverter();
  private final Function<Map<String, Object>, BytesReference> resultDocumentConverter =
    spyLambda(Function.class, smileConverter::toSmile);
  @Spy
  private SearchConfigurationProperties searchConfigurationProperties = getSearchConfigurationProperties();
  @Mock
  private RestHighLevelClient elasticsearchClient;
  @Captor
  private ArgumentCaptor<BulkRequest> bulkRequestCaptor;

  @BeforeEach
  void setUp() {
    repository.setElasticsearchClient(elasticsearchClient);
  }

  @Test
  void indexResources_positive_onlyIndexEvents() throws IOException {
    var document = subjectDocumentBodyToIndex();
    var bulkResponse = mock(BulkResponse.class);

    when(elasticsearchClient.bulk(bulkRequestCaptor.capture(), eq(DEFAULT))).thenReturn(bulkResponse);
    when(bulkResponse.hasFailures()).thenReturn(false);

    var actual = repository.indexResources(List.of(document));

    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
    assertThat(bulkRequestCaptor.getValue().requests()).hasSize(1).satisfies(requests -> {
      DocWriteRequest<?> request = requests.get(0);
      assertThat(request).isInstanceOf(UpdateRequest.class);
      var updateRequest = (UpdateRequest) request;
      assertThat(getParam(updateRequest.script(), "ins")).containsExactly(RESOURCE_ID);
      assertThat(getParam(updateRequest.script(), "del")).isEmpty();
    });
  }

  @Test
  void indexResources_positive_deleteSubjects() throws IOException {
    var subject1 = "java";
    var subject2 = "scala";

    var bulkResponse = mock(BulkResponse.class);
    when(elasticsearchClient.bulk(bulkRequestCaptor.capture(), eq(DEFAULT))).thenReturn(bulkResponse);
    when(bulkResponse.hasFailures()).thenReturn(false);

    var documents = List.of(subjectDocumentBodyToDelete(subject1), subjectDocumentBodyToDelete(subject2));
    var actual = repository.indexResources(documents);

    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
    var requests = bulkRequestCaptor.getValue().requests();
    assertThat(requests).hasSize(2);
    for (DocWriteRequest<?> request : requests) {
      var updateRequest = (UpdateRequest) request;
      assertThat(getParam(updateRequest.script(), "del")).containsExactly(RESOURCE_ID);
      assertThat(getParam(updateRequest.script(), "ins")).isEmpty();
    }
  }

  @Test
  void indexResources_positive_skipIfInstanceIdIsBlank() throws IOException {
    var subject = "scala";

    var bulkResponse = mock(BulkResponse.class);
    when(elasticsearchClient.bulk(bulkRequestCaptor.capture(), eq(DEFAULT))).thenReturn(bulkResponse);
    when(bulkResponse.hasFailures()).thenReturn(false);

    var documents = List.of(subjectDocumentBodyToDelete(subject, ""));
    var actual = repository.indexResources(documents);

    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
    var requests = bulkRequestCaptor.getValue().requests();
    assertThat(requests).isEmpty();
  }

  @SuppressWarnings("unchecked")
  private Set<String> getParam(Script script, String del) {
    return (Set<String>) script.getParams().get(del);
  }

  @SneakyThrows
  private SearchDocumentBody subjectDocumentBodyToIndex() {
    var subject = "test";
    var authorityId = randomId();
    var body = mapOf("value", subject, "instanceId", RESOURCE_ID, "authorityId", authorityId);
    var event = resourceEvent(getDocumentId(subject, authorityId), INSTANCE_SUBJECT_RESOURCE, CREATE, body, null);
    return SearchDocumentBody.of(new BytesArray(SMILE_MAPPER.writeValueAsBytes(body)), SMILE, event, INDEX);
  }

  @SneakyThrows
  private SearchDocumentBody subjectDocumentBodyToDelete(String subject) {
    return subjectDocumentBodyToDelete(subject, RESOURCE_ID);
  }

  @SneakyThrows
  private SearchDocumentBody subjectDocumentBodyToDelete(String subject, String instanceId) {
    var body = mapOf("value", subject, "instanceId", instanceId);
    var event =
      resourceEvent(getDocumentId(subject, null), INSTANCE_SUBJECT_RESOURCE, ResourceEventType.DELETE, null, body);
    return SearchDocumentBody.of(new BytesArray(SMILE_MAPPER.writeValueAsBytes(body)), SMILE, event, DELETE);
  }

  @NotNull
  private String getDocumentId(String subject, String authorityId) {
    return sha256Hex(subject + authorityId);
  }

  private SearchConfigurationProperties getSearchConfigurationProperties() {
    var indexSettings = new SearchConfigurationProperties.IndexingSettings();
    indexSettings.setDataFormat(SMILE);
    indexSettings.setInstanceSubjects(new SearchConfigurationProperties.DocumentIndexingSettings());
    var searchConfigurationProperties = new SearchConfigurationProperties();
    searchConfigurationProperties.setIndexing(indexSettings);
    return searchConfigurationProperties;
  }

}
