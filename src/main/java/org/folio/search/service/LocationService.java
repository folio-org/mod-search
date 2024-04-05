package org.folio.search.service;

import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.LOCATION_RESOURCE;

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

  public void reindex(String tenantId) {
    var processed = 0;
    var locationResponse = client.getLocations(processed, properties.getLocationBatchSize());
    var total = locationResponse.totalRecords();
    var locations = locationResponse.locations();

    indexLocations(tenantId, locations);
    processed += locations.size();
    log.info("reindex:: Successfully indexed {} of {} locations", processed, total);

    while (processed < total) {
      locations = client.getLocations(processed, properties.getLocationBatchSize()).locations();
      indexLocations(tenantId, locations);
      processed += locations.size();
      log.info("reindex:: Successfully indexed {} of {} locations", processed, total);
    }
  }

  private void indexLocations(String tenantId, List<Map<String, Object>> locations) {
    var events = locations.stream()
      .map(location -> toResourceEvent(tenantId, location))
      .toList();

    var indexResult = resourceService.indexResources(events);
    if (FolioIndexOperationResponse.StatusEnum.ERROR.equals(indexResult.getStatus())) {
      var errorMessage = "Indexing failed: " + indexResult.getErrorMessage();
      log.warn("reindex:: " + errorMessage);
      throw new IllegalStateException(errorMessage);
    }
  }

  private ResourceEvent toResourceEvent(String tenantId, Map<String, Object> location) {
    return new ResourceEvent()
      .id(getString(location, ID_FIELD) + "|" + tenantId)
      .tenant(tenantId)
      .resourceName(LOCATION_RESOURCE)
      ._new(location);
  }
}
