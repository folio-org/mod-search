package org.folio.search.service.reindex.jdbc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.search.model.types.ReindexEntityType;

public interface InstanceChildResourceRepository {

  void deleteByInstanceIds(List<String> instanceIds);

  void saveAll(Set<Map<String, Object>> entities, List<Map<String, Object>> entityRelations);

  ReindexEntityType entityType();
}
