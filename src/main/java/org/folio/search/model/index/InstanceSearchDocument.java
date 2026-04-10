package org.folio.search.model.index;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Flat document model for the instance search index. One document per source record (instance, holding, or item).
 */
@Data
@Builder
public class InstanceSearchDocument {

  private String id;
  private String resourceType;
  private String instanceId;
  private String tenantId;
  private boolean shared;

  /**
   * OpenSearch join field for parent-child relationships.
   * Instance docs: {"name": "instance"}
   * Holding docs: {"name": "holding", "parent": instanceId}
   * Item docs: {"name": "item", "parent": instanceId}
   */
  private Map<String, Object> joinField;

  /**
   * Source version for external versioning (prevents stale overwrites during concurrent writes).
   */
  private Long sourceVersion;

  /**
   * All enriched fields for this document, including resource-type-specific fields.
   */
  private Map<String, Object> fields;
}
