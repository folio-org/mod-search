package org.folio.search.repository;

import org.folio.search.model.config.FeatureConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeatureConfigRepository extends JpaRepository<FeatureConfigEntity, String> {
}
