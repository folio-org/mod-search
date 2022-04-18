package org.folio.search.repository;

import org.folio.search.model.streamids.StreamIdsJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StreamIdsJobRepository extends JpaRepository<StreamIdsJobEntity, String> {
}
