package org.folio.search.model.metadata;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.folio.search.model.types.SearchType;

/**
 * POJO class for specifying a plain field description for search engine.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlainFieldDescription extends FieldDescription {

  public static final String NONE_FIELD_TYPE = "none";
  public static final String MULTILANG_FIELD_TYPE = "multilang";

  /**
   * List of search types, that is used to identify search options for given field.
   */
  private List<SearchType> searchTypes = Collections.emptyList();

  /**
   * List of inventory search types, it can be used to create group of field using alias.
   */
  private List<String> inventorySearchTypes = Collections.emptyList();

  /**
   * List of references to groups, where values can be combined in one elasticsearch field.
   */
  private List<String> group;

  /**
   * List of references to field types, specified in resource description.
   */
  private String index;

  /**
   * Specifies if fields should be returned as part of elasticsearch response or not.
   */
  private boolean showInResponse;

  /**
   * Search term processor, which pre-processes incoming term for elasticsearch request.
   */
  private String searchTermProcessor;

  /**
   * Elasticsearch fields mappings.
   *
   * <p>Resource description processor will take this field without any modification and put it to
   * elasticsearch.</p>
   */
  private ObjectNode mappings;

  /**
   * Checks if resource description field is multi-language.
   *
   * @return true if field is must be indexed, false - otherwise
   */
  @JsonIgnore
  public boolean isMultilang() {
    return MULTILANG_FIELD_TYPE.equals(index);
  }

  /**
   * Checks if resource description field should be indexed or not.
   *
   * @return true if field is must be indexed, false - otherwise
   */
  @JsonIgnore
  public boolean isIndexed() {
    return !NONE_FIELD_TYPE.equals(index);
  }

  /**
   * Checks if field description contains given {@link SearchType} value.
   *
   * @return true - field has given search type, false - otherwise
   */
  @JsonIgnore
  public boolean hasType(SearchType searchType) {
    return searchTypes.contains(searchType);
  }
}
