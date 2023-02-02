package org.folio.search.model.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "language_config")
@Entity
public class LanguageConfigEntity {

  @Id
  private String code;

  @Column(name = "es_analyzer")
  private String esAnalyzer;
}
