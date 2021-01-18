package org.folio.search.model.metadata;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.folio.search.model.types.InventorySearchType;
import org.folio.search.model.types.SearchFieldType;

/**
 * POJO class for specifying a plain field description for search engine.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlainFieldDescription extends FieldDescription {

  public static final String NONE_FIELD_TYPE = "none";
  public static final String MULTILANG_FIELD_TYPE = "multilang";

  /**
   * List of field types for search possibilities, like faceting, sorting and so on.
   *
   * <p>Mapping of this field should provide ability to perform requested operation</p>
   */
  private List<SearchFieldType> searchFieldTypes;

  /**
   * List of search types.
   */
  private List<InventorySearchType> inventorySearchTypes;

  /**
   * List of references to groups, where values can be combined in one elasticsearch field.
   */
  private List<String> group;

  /**
   * List of references to field types, specified in resource description.
   */
  private String index;

  /**
   * Specifies if field can be used as language source.
   */
  private boolean languageSource;

  /**
   * Elasticsearch fields mappings.
   *
   * <p>Resource description processor will take this field without any modification and put it to
   * elasticsearch.</p>
   */
  private ObjectNode mappings;

  /**
   * JSON path to the value from incoming event. This value or set of values will be indexed in search engine.
   */
  private String sourcePath;
}
