package org.folio.search.integration;

import static java.util.Arrays.asList;
import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.CLASSIFICATION_TYPES;
import static org.folio.search.configuration.SearchCacheNames.REFERENCE_DATA_CACHE;
import static org.folio.search.model.client.CqlQueryParam.NAME;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.model.types.ClassificationType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClassificationTypeHelper {

  private final ReferenceDataService referenceDataService;
  private final SearchConfigurationProperties configurationProperties;

  @Cacheable(cacheNames = REFERENCE_DATA_CACHE,
             unless = "#result.isEmpty()",
             key = "@folioExecutionContext.tenantId + ':classification-types'")
  public Map<String, ClassificationType> getClassificationTypeMap() {
    var browseClassificationTypes = configurationProperties.getBrowseClassificationTypes();
    if (browseClassificationTypes.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, ClassificationType> result = new HashMap<>();

    for (var typeEntry : browseClassificationTypes.entrySet()) {
      var ids = referenceDataService.fetchReferenceData(CLASSIFICATION_TYPES, NAME, asList(typeEntry.getValue()));
      ids.forEach(id -> result.put(id, typeEntry.getKey()));
    }

    return result;
  }

}
