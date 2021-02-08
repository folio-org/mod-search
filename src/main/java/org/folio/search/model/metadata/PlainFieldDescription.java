package org.folio.search.model.metadata;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
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
   * Specifies if field can be used as language source.
   */
  private boolean languageSource;

  /**
   * Specifies if fields should be returned as part of elasticsearch response or not.
   */
  private boolean showInResponse;

  /**
   * Elasticsearch fields mappings.
   *
   * <p>Resource description processor will take this field without any modification and put it to
   * elasticsearch.</p>
   */
  private ObjectNode mappings;

  private String populatedBy;

  public boolean isMultilang() {
    return MULTILANG_FIELD_TYPE.equals(index);
  }

  public boolean hasPopulatedBy() {
    return StringUtils.isNotBlank(populatedBy);
  }
}
