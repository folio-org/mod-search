package org.folio.search.service.reindex.jdbc;

import java.util.List;
import org.folio.search.model.entity.ChildResourceEntityBatch;
import org.folio.search.model.types.ReindexEntityType;

public interface InstanceChildResourceRepository {

  void deleteByInstanceIds(List<String> instanceIds, String tenant);

  void saveAll(ChildResourceEntityBatch childResourceEntityBatch);

  ReindexEntityType entityType();
}
