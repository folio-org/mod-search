package org.folio.search.service.indexrebuild;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.types.ResourceType;

/**
 * An index that can be rebuilt independently. {@code dataVersion} has to be bumped manually when a change affects the
 * produced documents without changing the index mappings, for example a change of document conversion logic.
 */
@Getter
@RequiredArgsConstructor
public enum IndexRebuildTarget {

  ITEM("item", ResourceType.ITEM, Source.RECORDS, 1, List.of()),
  CALL_NUMBER("call_number", ResourceType.INSTANCE_CALL_NUMBER, Source.INDEX, 1, List.of(ITEM));

  private final String resource;
  private final ResourceType resourceType;
  private final Source source;
  private final int dataVersion;
  private final List<IndexRebuildTarget> dependsOn;

  /**
   * Returns the target of a resource name.
   *
   * @throws IllegalArgumentException if no target has that resource name
   */
  public static IndexRebuildTarget fromResource(String resource) {
    return Arrays.stream(values())
      .filter(target -> target.resource.equals(resource))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Unknown index rebuild resource: " + resource));
  }

  /**
   * Whether the index is rebuilt from inventory records. Deletes of such records are tracked while a run is active.
   */
  public boolean isBuiltFromRecords() {
    return source == Source.RECORDS;
  }

  public enum Source {

    /**
     * One inventory record becomes one document.
     */
    RECORDS,

    /**
     * Documents are aggregated from the documents of the indexes the target depends on.
     */
    INDEX
  }
}
