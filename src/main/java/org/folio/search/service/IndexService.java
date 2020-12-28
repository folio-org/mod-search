package org.folio.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.ResourceEventBody;
import org.folio.search.model.rest.response.FolioCreateIndexResponse;
import org.folio.search.model.rest.response.FolioIndexResourceResponse;
import org.folio.search.model.rest.response.FolioPutMappingResponse;
import org.folio.search.repository.IndexRepository;
import org.folio.search.service.es.SearchMappingsHelper;
import org.folio.search.service.es.SearchSettingsHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IndexService {

  private final IndexRepository indexRepository;
  private final SearchMappingsHelper mappingHelper;
  private final SearchSettingsHelper settingsHelper;
  private final SearchDocumentConverter searchDocumentConverter;

  /**
   * Creates index for resource with pre-defined settings and mappings.
   *
   * @param resourceName name of resource as {@link String} value.
   * @param tenantId tenant id as {@link String} value.
   * @return {@link FolioCreateIndexResponse} if index was created successfully
   * @throws SearchServiceException if {@link IOException} has been occurred during execution request to
   *   elasticsearch
   */
  public FolioCreateIndexResponse createIndex(String resourceName, String tenantId) {
    var index = getIndexName(resourceName, tenantId);
    var settings = settingsHelper.getSettings(resourceName);
    var mappings = mappingHelper.getMappings(resourceName);
    return indexRepository.createIndex(index, settings, mappings);
  }

  /**
   * Updates elasticsearch index mappings for resource.
   *
   * @param resourceName resource name as {@link String} value.
   * @param tenantId tenant id as {@link String} value.
   * @return {@link AcknowledgedResponse} object.
   */
  public FolioPutMappingResponse updateMappings(String resourceName, String tenantId) {
    var index = getIndexName(resourceName, tenantId);
    var mappings = mappingHelper.getMappings(resourceName);
    return indexRepository.updateMappings(index, mappings);
  }

  /**
   * Saves list of resources to elasticsearch.
   *
   * @param resources {@link List} of resources as {@link JsonNode} objects.
   */
  public FolioIndexResourceResponse indexResources(List<ResourceEventBody> resources) {
    if (CollectionUtils.isEmpty(resources)) {
      return FolioIndexResourceResponse.success();
    }
    var elasticsearchDocuments = searchDocumentConverter.convert(resources);
    return indexRepository.indexResources(elasticsearchDocuments);
  }

  private static String getIndexName(String resource, String tenantId) {
    return resource + "_" + tenantId;
  }
}
