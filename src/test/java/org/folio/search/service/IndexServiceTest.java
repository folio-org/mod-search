package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.SearchResponseHelper.getSuccessFolioCreateIndexResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.searchDocumentBody;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.folio.search.repository.IndexRepository;
import org.folio.search.service.converter.SearchDocumentConverter;
import org.folio.search.service.es.SearchMappingsHelper;
import org.folio.search.service.es.SearchSettingsHelper;
import org.folio.search.utils.TestUtils;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IndexServiceTest {

  private static final String RESOURCE_NAME = "test-resource";
  private static final String TENANT_ID = "test-tenant";
  public static final String INDEX_NAME = RESOURCE_NAME + "_" + TENANT_ID;
  private static final String EMPTY_OBJECT = "{}";
  @Mock private IndexRepository indexRepository;
  @Mock private SearchMappingsHelper mappingsHelper;
  @Mock private SearchSettingsHelper settingsHelper;
  @Mock private SearchDocumentConverter searchDocumentConverter;
  @InjectMocks private IndexService indexService;

  @Test
  void createIndex() {
    var expectedResponse = getSuccessFolioCreateIndexResponse(List.of(INDEX_NAME));

    when(mappingsHelper.getMappings(RESOURCE_NAME)).thenReturn(EMPTY_OBJECT);
    when(settingsHelper.getSettings(RESOURCE_NAME)).thenReturn(EMPTY_OBJECT);
    when(indexRepository.createIndex(INDEX_NAME, EMPTY_OBJECT, EMPTY_OBJECT))
      .thenReturn(expectedResponse);

    var indexResponse = indexService.createIndex(RESOURCE_NAME, TENANT_ID);
    assertThat(indexResponse).isEqualTo(expectedResponse);
  }

  @Test
  void updateMappings() {
    var expectedResponse = getSuccessIndexOperationResponse();

    when(mappingsHelper.getMappings(RESOURCE_NAME)).thenReturn(EMPTY_OBJECT);
    when(indexRepository.updateMappings(INDEX_NAME, EMPTY_OBJECT)).thenReturn(expectedResponse);

    var indexResponse = indexService.updateMappings(RESOURCE_NAME, TENANT_ID);
    assertThat(indexResponse).isEqualTo(expectedResponse);
  }

  @Test
  void indexResources_positive() {
    var searchBody = searchDocumentBody();
    var eventBody = TestUtils.eventBody(RESOURCE_NAME, mapOf("id", randomId()));
    var expectedResponse = getSuccessIndexOperationResponse();

    when(searchDocumentConverter.convert(List.of(eventBody))).thenReturn(List.of(searchBody));
    when(indexRepository.indexResources(List.of(searchBody))).thenReturn(expectedResponse);

    var response = indexService.indexResources(List.of(eventBody));
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  void indexResources_positive_emptyList() {
    var response = indexService.indexResources(Collections.emptyList());
    assertThat(response).isEqualTo(getSuccessIndexOperationResponse());
  }
}
