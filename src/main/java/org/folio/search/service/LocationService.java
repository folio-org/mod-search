package org.folio.search.service;

import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.utils.SearchUtils.ID_FIELD;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.client.LocationsClient;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ResourceEvent;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class LocationService {

  private final ReindexConfigurationProperties properties;
  private final LocationsClient client;
  private final ResourceService resourceService;

  public void reindex(String tenantId, String resourceName) {
    int processed = 0;
    int total;
    do {
      var uri = LocationsClient.DocumentType.valueOf(resourceName.toUpperCase()).getUri();
      var locationDataResponse = client.getLocationsData(uri, processed, properties.getLocationBatchSize());
      total = locationDataResponse.getTotalRecords();

      var locations = locationDataResponse.getResult();
      processed += locations.size();
      indexLocationData(tenantId, resourceName, locations);

      log.info("reindexLocations-{}:: Successfully indexed {} of {} location documents", resourceName,
        processed, total);
    } while (processed < total);
  }

  private void indexLocationData(String tenantId, String resourceName, List<Map<String, Object>> locationData) {
    var events = locationData.stream()
      .map(locationDataEntry -> toResourceEvent(tenantId, resourceName, locationDataEntry))
      .toList();

    var indexResult = resourceService.indexResources(events);
    if (FolioIndexOperationResponse.StatusEnum.ERROR.equals(indexResult.getStatus())) {
      var errorMessage = "Indexing failed: " + indexResult.getErrorMessage();

      log.warn("reindexLocations-{}:: {}", resourceName, errorMessage);

      throw new IllegalStateException(errorMessage);
    }
  }

  private ResourceEvent toResourceEvent(String tenantId, String resourceName, Map<String, Object> locationData) {
    return new ResourceEvent()
      .id(getString(locationData, ID_FIELD) + "|" + tenantId)
      .tenant(tenantId)
      .resourceName(resourceName)
      ._new(locationData);
  }
}
