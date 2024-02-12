package org.folio.search.repository.classification;

import java.util.List;

public interface InstanceClassificationRepository {

  void saveAll(List<InstanceClassificationEntity> classifications);

  void deleteAll(List<InstanceClassificationEntity> classifications);

  List<InstanceClassificationEntity> findAll();

  List<InstanceClassificationEntityAgg> fetchAggregatedByClassifications(
    List<InstanceClassificationEntity> classifications);
}
