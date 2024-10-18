package org.folio.search.service.reindex;

import static java.util.function.Function.identity;
import static org.folio.search.service.reindex.ReindexConstants.RESOURCE_NAME_MAP;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.IndexService;
import org.folio.search.service.reindex.jdbc.ReindexJdbcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
public class ReindexCommonService {

  private final Map<ReindexEntityType, ReindexJdbcRepository> repositories;
  private final IndexService indexService;

  public ReindexCommonService(List<ReindexJdbcRepository> repositories, IndexService indexService) {
    this.repositories = repositories.stream()
      .collect(Collectors.toMap(ReindexJdbcRepository::entityType, identity(), (rep1, rep2) -> rep2));
    this.indexService = indexService;
  }

  @Transactional
  public void deleteAllRecords() {
    for (ReindexEntityType entityType : ReindexEntityType.values()) {
      repositories.get(entityType).truncate();
    }
  }

  public void recreateIndex(ReindexEntityType reindexEntityType, String tenantId, IndexSettings indexSettings) {
    try {
      var resourceType = RESOURCE_NAME_MAP.get(reindexEntityType);
      indexService.dropIndex(resourceType, tenantId);
      indexService.createIndex(resourceType, tenantId, indexSettings);
    } catch (Exception e) {
      log.warn("Index cannot be recreated for resource={}, message={}", reindexEntityType, e.getMessage());
    }
  }
}
