package org.folio.search.model.metadata;

import static org.folio.search.utils.SearchUtils.PLAIN_MULTILANG_PREFIX;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
  public static final String STANDARD_FIELD_TYPE = "standard";
  public static final Set<String> FULLTEXT_FIELD_TYPES = Set.of(MULTILANG_FIELD_TYPE, STANDARD_FIELD_TYPE);
  public static final String PLAIN_MULTILANG_FIELD_TYPE = PLAIN_MULTILANG_PREFIX + "multilang";

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
   * Allows to specify default value when not present or null.
   */
  @JsonProperty("default")
  private Object defaultValue;

  /**
   * Specifies if plain keyword value should be indexed with field or not. Works only for fulltext fields.
   */
  private boolean indexPlainValue = true;

  /**
   * Provides sort description for field. If not specified - standard rules will be applied for sort field.
   */
  @JsonProperty("sort")
  private SortDescription sortDescription;

  /**
   * Checks if resource description field is multi-language.
   *
   * @return true if field is multi-language, false - otherwise
   */
  @JsonIgnore
  public boolean isMultilang() {
    return MULTILANG_FIELD_TYPE.equals(index);
  }

  /**
   * Checks if resource description field can be considered as fulltext.
   *
   * @return true if field can be considered as a fulltext, false - otherwise
   */
  @JsonProperty
  public boolean hasFulltextIndex() {
    return FULLTEXT_FIELD_TYPES.contains(index);
  }

  /**
   * Checks if resource description field should be indexed or not.
   *
   * @return true if field is must be indexed, false - otherwise
   */
  @JsonIgnore
  public boolean isNotIndexed() {
    return NONE_FIELD_TYPE.equals(index);
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
