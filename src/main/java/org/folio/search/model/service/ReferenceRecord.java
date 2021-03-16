package org.folio.search.model.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor(staticName = "referenceRecord")
@NoArgsConstructor
public class ReferenceRecord {
  private String id;
  private String name;
}
