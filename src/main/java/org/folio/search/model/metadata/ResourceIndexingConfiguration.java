package org.folio.search.model.metadata;

import lombok.Data;

@Data
public class ResourceIndexingConfiguration {

  /**
   * Spring bean name which must be called to populate / pre-process the whole resource event.
   *
   * <p>It can be used to populate new event for secondary resources or divide one event to multiple, as it done for
   * authority search</p>
   */
  private String eventPreProcessor;

  /**
   * Spring bean name which must be called to index resource event batch.
   */
  private String resourceRepository;
}
