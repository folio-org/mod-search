package org.folio.search.service.reindex.jdbc;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface InstanceChildResourceRepository {

  void updateCounts(List<Map<String, Object>> entities);

  void saveAll(Set<Map<String, Object>> entities, List<Map<String, Object>> entityRelations);
}
