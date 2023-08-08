package org.folio.search.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.model.types.IndexingDataFormat.SMILE;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.CONTRIBUTOR_RESOURCE;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.SneakyThrows;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.search.service.consortium.TenantProvider;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.SmileConverter;
import org.folio.spring.test.type.UnitTest;
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
class InstanceContributorsRepositoryTest {

  @InjectMocks
  private InstanceContributorsRepository repository;

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
  @Mock
  private TenantProvider tenantProvider;
  @Mock
  private ConsortiumTenantService consortiumTenantService;
  @Captor
  private ArgumentCaptor<BulkRequest> bulkRequestCaptor;

  @BeforeEach
  void setUp() {
    repository.setElasticsearchClient(elasticsearchClient);
    repository.setTenantProvider(tenantProvider);
  }

  @Test
  void indexResources_positive() throws IOException {
    var typeId = randomId();
    var document = contributorDocumentBodyToIndex(typeId);
    var bulkResponse = mock(BulkResponse.class);

    when(elasticsearchClient.bulk(bulkRequestCaptor.capture(), eq(DEFAULT))).thenReturn(bulkResponse);
    when(bulkResponse.hasFailures()).thenReturn(false);

    var actual = repository.indexResources(List.of(document));

    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
    assertThat(bulkRequestCaptor.getValue().requests()).hasSize(1).satisfies(requests -> {
      DocWriteRequest<?> request = requests.get(0);
      assertThat(request).isInstanceOf(UpdateRequest.class);
      var updateRequest = (UpdateRequest) request;
      assertThat(getParam(updateRequest.script(), "ins"))
        .containsExactly(Map.of("instanceId", RESOURCE_ID,
          "tenantId", TENANT_ID, "typeId", typeId));
      assertThat(getParam(updateRequest.script(), "del")).isEmpty();
    });
  }

  @Test
  void indexResources_positive_shared() throws IOException {
    var typeId = randomId();
    var document = contributorDocumentBodyToIndex(typeId);
    var bulkResponse = mock(BulkResponse.class);

    when(elasticsearchClient.bulk(bulkRequestCaptor.capture(), eq(DEFAULT))).thenReturn(bulkResponse);
    when(bulkResponse.hasFailures()).thenReturn(false);
    when(consortiumTenantService.getCentralTenant(TENANT_ID)).thenReturn(Optional.of(TENANT_ID));

    var actual = repository.indexResources(List.of(document));

    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
    assertThat(bulkRequestCaptor.getValue().requests()).hasSize(1).satisfies(requests -> {
      DocWriteRequest<?> request = requests.get(0);
      assertThat(request).isInstanceOf(UpdateRequest.class);
      var updateRequest = (UpdateRequest) request;
      assertThat(getParam(updateRequest.script(), "ins"))
        .containsExactly(Map.of("instanceId", RESOURCE_ID,
          "typeId", typeId,
          "tenantId", TENANT_ID,
          "shared", true));
      assertThat(getParam(updateRequest.script(), "del")).isEmpty();
    });
  }

  @SuppressWarnings("unchecked")
  private Set<Map<String, Object>> getParam(Script script, String param) {
    return (Set<Map<String, Object>>) script.getParams().get(param);
  }

  @SneakyThrows
  private SearchDocumentBody contributorDocumentBodyToIndex(String typeId) {
    var id = randomId();
    var authorityId = randomId();
    var body = mapOf("id", id, "instanceId", RESOURCE_ID, "name", "test", "nameTypeId", randomId(),
      "typeId", typeId, "authorityId", authorityId);
    var event = resourceEvent(id, CONTRIBUTOR_RESOURCE, CREATE, body, null);
    return SearchDocumentBody.of(new BytesArray(SMILE_MAPPER.writeValueAsBytes(body)), SMILE, event, INDEX);
  }

  private SearchConfigurationProperties getSearchConfigurationProperties() {
    var indexSettings = new SearchConfigurationProperties.IndexingSettings();
    indexSettings.setDataFormat(SMILE);
    indexSettings.setInstanceContributors(new SearchConfigurationProperties.DocumentIndexingSettings());
    var searchConfigurationProperties = new SearchConfigurationProperties();
    searchConfigurationProperties.setIndexing(indexSettings);
    return searchConfigurationProperties;
  }

}
