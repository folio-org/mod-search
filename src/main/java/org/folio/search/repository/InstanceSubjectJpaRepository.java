package org.folio.search.repository;

import java.util.List;
import org.folio.search.model.entity.InstanceSubjectEntity;
import org.folio.search.model.entity.InstanceSubjectEntityId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InstanceSubjectJpaRepository extends JpaRepository<InstanceSubjectEntity, InstanceSubjectEntityId> {

  @Query(nativeQuery = true, value = "select subject, count(*) "
    + "from instance_subjects where subject in ("
    + "select distinct on (lower(subject)) subject from instance_subjects "
    + "where lower(subject) >= lower(:anchor) order by lower(subject) limit :limit"
    + ") group by subject order by lower(subject);")
  List<Object> browseForwardIncluding(@Param("anchor") String anchor, @Param("limit") int limit);
}
