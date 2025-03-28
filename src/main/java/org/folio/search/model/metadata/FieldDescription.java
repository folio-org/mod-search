package org.folio.search.model.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import lombok.Data;
import org.folio.search.model.types.FieldType;

@Data
@JsonTypeInfo(
  use = Id.NAME,
  property = "type",
  defaultImpl = PlainFieldDescription.class,
  visible = true)
@JsonSubTypes({
  @Type(value = PlainFieldDescription.class, name = "plain"),
  @Type(value = SearchFieldDescriptor.class, name = "search"),
  @Type(value = ObjectFieldDescription.class, name = "object"),
  @Type(value = NestedFieldDescription.class, name = "nested"),
  @Type(value = AuthorityFieldDescription.class, name = "authority"),
})
public abstract class FieldDescription {

  /**
   * Field type.
   */
  private FieldType type;

  /**
   * Pre-defined type for the field, if applicable.
   */
  @JsonProperty("$type")
  private String fieldType;
}
