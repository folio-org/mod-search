package org.folio.search.model.config;

import java.util.UUID;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
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
  private UUID id;
  private String code;
}
