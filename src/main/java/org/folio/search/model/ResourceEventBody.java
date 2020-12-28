package org.folio.search.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class ResourceEventBody {

  /**
   * Operation type.
   */
  private String type;

  /**
   * Tenant id.
   */
  private String tenant;

  /**
   * Resource name.
   */
  private String resourceName;

  /**
   * Data from update.
   */
  @JsonProperty("new")
  private ObjectNode newData;

  /**
   * Adds resource name to the name.
   *
   * @param resourceName name of resource.
   * @return {@link ResourceEventBody} object
   */
  public ResourceEventBody withResourceName(String resourceName) {
    this.resourceName = resourceName;
    return this;
  }
}
