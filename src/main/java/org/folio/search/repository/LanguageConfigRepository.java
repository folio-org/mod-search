package org.folio.search.repository;

import org.folio.search.model.config.LanguageConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LanguageConfigRepository extends JpaRepository<LanguageConfigEntity, String> {
}
