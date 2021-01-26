package org.folio.search.repository;

import java.util.UUID;
import org.folio.search.model.config.LanguageConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LanguageConfigRepository extends JpaRepository<LanguageConfigEntity, UUID> {
}
