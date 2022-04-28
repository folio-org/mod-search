package org.folio.search.repository;

import org.folio.search.model.streamids.ResourceIdsJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceIdsJobRepository extends JpaRepository<ResourceIdsJobEntity, String> {

  @Query(nativeQuery = true, value =
    "SELECT * FROM resource_ids_job as rij "
      + "WHERE rij.query= :query AND rij.status = 'COMPLETED' "
      + "ORDER BY created_date DESC LIMIT 1")
  ResourceIdsJobEntity getLastActualJob(@Param("query") String query);
}
