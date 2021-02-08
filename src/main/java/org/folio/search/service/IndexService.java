package org.folio.search.service;

import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.repository.IndexRepository;
import org.folio.search.service.converter.ConvertConfig;
import org.folio.search.service.converter.SearchDocumentConverter;
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
  private final LanguageConfigService languageConfigService;

  /**
   * Creates index for resource with pre-defined settings and mappings.
   *
   * @param resourceName name of resource as {@link String} value.
   * @param tenantId tenant id as {@link String} value.
   * @return {@link FolioCreateIndexResponse} if index was created successfully
   * @throws SearchServiceException if {@link IOException} has been occurred during execution request to
   *     elasticsearch
   */
  public FolioCreateIndexResponse createIndex(String resourceName, String tenantId) {
    var index = getElasticsearchIndexName(resourceName, tenantId);
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
  public FolioIndexOperationResponse updateMappings(String resourceName, String tenantId) {
    var index = getElasticsearchIndexName(resourceName, tenantId);
    var mappings = mappingHelper.getMappings(resourceName);
    return indexRepository.updateMappings(index, mappings);
  }

  /**
   * Saves list of resources to elasticsearch.
   *
   * @param resources {@link List} of resources as {@link JsonNode} objects.
   */
  public FolioIndexOperationResponse indexResources(List<ResourceEventBody> resources) {
    if (CollectionUtils.isEmpty(resources)) {
      return getSuccessIndexOperationResponse();
    }

    final ConvertConfig convertConfig = new ConvertConfig();
    resources.stream()
      .map(ResourceEventBody::getTenant)
      .distinct()
      .forEach(tenant -> convertConfig.addSupportedLanguage(tenant,
        languageConfigService.getAllLanguagesForTenant(tenant)));

    var elasticsearchDocuments = searchDocumentConverter.convert(convertConfig, resources);
    return indexRepository.indexResources(elasticsearchDocuments);
  }
}
