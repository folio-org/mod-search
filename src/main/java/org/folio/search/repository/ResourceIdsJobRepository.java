package org.folio.search.repository;

import org.folio.search.model.streamids.ResourceIdsJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceIdsJobRepository extends JpaRepository<ResourceIdsJobEntity, String> {
  
}
