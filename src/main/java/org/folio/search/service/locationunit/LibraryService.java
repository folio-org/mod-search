package org.folio.search.service.locationunit;

import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.LIBRARY_RESOURCE;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.client.LibrariesClient;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.service.ResourceService;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class LibraryService {

  private final ReindexConfigurationProperties properties;
  private final LibrariesClient client;
  private final ResourceService resourceService;

  public void reindex(String tenantId) {
    int processed = 0;
    int total;
    do {
      var libraryResponse = client.getLibraries(processed, properties.getLibraryBatchSize());
      total = libraryResponse.totalRecords();

      var libraries = libraryResponse.loclibs();
      processed += libraries.size();
      indexLibraries(tenantId, libraries);

      log.info("reindex:: Successfully indexed {} of {} libraries", processed, total);
    } while (processed < total);
  }

  private void indexLibraries(String tenantId, List<Map<String, Object>> libraries) {
    var events = libraries.stream()
      .map(location -> toResourceEvent(tenantId, location))
      .toList();

    var indexResult = resourceService.indexResources(events);
    if (FolioIndexOperationResponse.StatusEnum.ERROR.equals(indexResult.getStatus())) {
      var errorMessage = "Indexing failed: " + indexResult.getErrorMessage();

      log.warn("indexLibraries:: Unsuccessful, {}", errorMessage);

      throw new IllegalStateException(errorMessage);
    }
  }

  private ResourceEvent toResourceEvent(String tenantId, Map<String, Object> libraries) {
    return new ResourceEvent()
      .id(getString(libraries, ID_FIELD) + "|" + tenantId)
      .tenant(tenantId)
      .resourceName(LIBRARY_RESOURCE)
      ._new(libraries);
  }
}
