package org.folio.search.service.reindex.jdbc;

import java.util.List;
import org.folio.search.model.entity.ChildResourceEntityBatch;

public interface InstanceChildResourceRepository {

  void deleteByInstanceIds(List<String> instanceIds);

  void saveAll(ChildResourceEntityBatch childResourceEntityBatch);
}
