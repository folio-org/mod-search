package org.folio.search.repository;

import java.util.List;
import org.folio.search.model.config.BrowseConfigEntity;
import org.folio.search.model.config.BrowseConfigId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrowseConfigEntityRepository extends JpaRepository<BrowseConfigEntity, BrowseConfigId> {

  List<BrowseConfigEntity> findByConfigId_BrowseType(String browseType);
}
