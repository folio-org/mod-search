package org.folio.search.repository;

import java.util.Date;
import java.util.List;
import org.folio.search.model.streamids.ResourceIdsJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ResourceIdsJobRepository extends JpaRepository<ResourceIdsJobEntity, String> {

  @Transactional
  @Modifying
  @Query(value = "delete from resource_ids_job r where created_date < :createdDate returning temp_table_name",
         nativeQuery = true)
  List<String> deleteByCreatedDateLessThan(Date createdDate);
}
