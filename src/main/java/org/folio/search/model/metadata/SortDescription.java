package org.folio.search.model.metadata;

import static java.util.Collections.emptyList;
import static org.folio.search.model.types.SortFieldType.SINGLE;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import org.folio.search.model.types.SortFieldType;

@Data
public class SortDescription {

  /**
   * Custom field name, if it is not specified - default strategy will be applied: sort_${fieldName}.
   */
  private String fieldName;

  /**
   * Sort field type.
   */
  @JsonProperty("type")
  private SortFieldType sortType = SINGLE;

  /**
   * List of fields for secondary sorting.
   */
  private List<String> secondarySort = emptyList();
}
