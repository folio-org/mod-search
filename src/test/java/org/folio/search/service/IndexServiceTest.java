package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.searchDocumentBody;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.folio.search.model.ResourceEventBody;
import org.folio.search.model.rest.response.FolioCreateIndexResponse;
import org.folio.search.model.rest.response.FolioIndexResourceResponse;
import org.folio.search.model.rest.response.FolioPutMappingResponse;
import org.folio.search.repository.IndexRepository;
import org.folio.search.service.es.SearchMappingsHelper;
import org.folio.search.service.es.SearchSettingsHelper;
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
  @Mock private SearchDocumentMapper searchDocumentMapper;
  @InjectMocks private IndexService indexService;

  @Test
  void createIndex() {
    var expectedResponse = FolioCreateIndexResponse.success(List.of(INDEX_NAME));

    when(mappingsHelper.getMappings(RESOURCE_NAME)).thenReturn(EMPTY_OBJECT);
    when(settingsHelper.getSettings(RESOURCE_NAME)).thenReturn(EMPTY_OBJECT);
    when(indexRepository.createIndex(INDEX_NAME, EMPTY_OBJECT, EMPTY_OBJECT))
      .thenReturn(expectedResponse);

    var indexResponse = indexService.createIndex(RESOURCE_NAME, TENANT_ID);
    assertThat(indexResponse).isEqualTo(expectedResponse);
  }

  @Test
  void updateMappings() {
    var expectedResponse = FolioPutMappingResponse.success();

    when(mappingsHelper.getMappings(RESOURCE_NAME)).thenReturn(EMPTY_OBJECT);
    when(indexRepository.updateMappings(INDEX_NAME, EMPTY_OBJECT)).thenReturn(expectedResponse);

    var indexResponse = indexService.updateMappings(RESOURCE_NAME, TENANT_ID);
    assertThat(indexResponse).isEqualTo(expectedResponse);
  }

  @Test
  void indexResources_positive() {
    var searchBody = searchDocumentBody();
    var resourceData = jsonObject("id", randomId());
    var eventBody = ResourceEventBody.of("CREATE", TENANT_ID, RESOURCE_NAME, resourceData);
    var expectedResponse = FolioIndexResourceResponse.success();

    when(searchDocumentMapper.convert(List.of(eventBody))).thenReturn(List.of(searchBody));
    when(indexRepository.indexResources(List.of(searchBody))).thenReturn(expectedResponse);

    var response = indexService.indexResources(List.of(eventBody));
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  void indexResources_positive_emptyList() {
    var response = indexService.indexResources(Collections.emptyList());
    assertThat(response).isEqualTo(FolioIndexResourceResponse.success());
  }
}
