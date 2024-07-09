package org.folio.search.service.locationunit;

import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.utils.SearchUtils.CAMPUS_RESOURCE;
import static org.folio.search.utils.SearchUtils.ID_FIELD;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.client.CampusesClient;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.service.ResourceService;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class CampusService {

  private final ReindexConfigurationProperties properties;
  private final CampusesClient client;
  private final ResourceService resourceService;

  public void reindex(String tenantId) {
    int processed = 0;
    int total;
    do {
      var campusResponse = client.getCampuses(processed, properties.getCampusBatchSize());
      total = campusResponse.totalRecords();

      var campuses = campusResponse.loccamps();
      processed += campuses.size();
      indexCampuses(tenantId, campuses);

      log.info("reindex:: Successfully indexed {} of {} campuses", processed, total);
    } while (processed < total);
  }

  private void indexCampuses(String tenantId, List<Map<String, Object>> campuses) {
    var events = campuses.stream()
      .map(location -> toResourceEvent(tenantId, location))
      .toList();

    var indexResult = resourceService.indexResources(events);
    if (FolioIndexOperationResponse.StatusEnum.ERROR.equals(indexResult.getStatus())) {
      var errorMessage = "Indexing failed: " + indexResult.getErrorMessage();

      log.warn("indexCampus:: Unsuccessful, {}", errorMessage);

      throw new IllegalStateException(errorMessage);
    }
  }

  private ResourceEvent toResourceEvent(String tenantId, Map<String, Object> campuses) {
    return new ResourceEvent()
      .id(getString(campuses, ID_FIELD) + "|" + tenantId)
      .tenant(tenantId)
      .resourceName(CAMPUS_RESOURCE)
      ._new(campuses);
  }
}
