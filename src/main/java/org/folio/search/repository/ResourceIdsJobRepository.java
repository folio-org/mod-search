package org.folio.search.repository;

import org.folio.search.model.streamids.ResourceIdsJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResourceIdsJobRepository extends JpaRepository<ResourceIdsJobEntity, String> {

}
