package org.folio.search.service.indexrebuild.derived;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.opensearch.index.query.QueryBuilder;

/**
 * What differs between indexes derived from another index: each document aggregates all source documents sharing a
 * key. Source documents carry the key in {@link #keyField()}, set when they are indexed. Everything else (key ranges,
 * reading, writing, finalization, the queue of changed keys and its refresh) is shared by the derived pipeline.
 */
public interface DerivedRebuildStrategy {

  IndexRebuildTarget target();

  /**
   * Index the documents are built from. It has to be one of the dependencies of the target.
   */
  IndexRebuildTarget source();

  /**
   * Whether the index is enabled for the tenant in context.
   */
  boolean isEnabled();

  /**
   * Checks that the index is enabled for the tenant in context.
   *
   * @throws RequestValidationException if it isn't
   */
  void validateEnabled();

  /**
   * Whether keys changed by events of the source are queued and refreshed at runtime.
   */
  boolean isTrackingEnabled();

  /**
   * Field of source documents holding their keys: a string, or a list of strings.
   */
  String keyField();

  /**
   * Fields of source documents the documents are built from.
   */
  String[] sourceFields();

  /**
   * Keys of a source record as it arrives in an event, before it is indexed.
   */
  Set<String> keysOf(Map<?, ?> sourceRecord);

  /**
   * Field of documents of the target holding their key, so documents can be selected by a key range.
   */
  String documentKeyField();

  /**
   * Query for documents of the target that reference the given tenant.
   */
  QueryBuilder documentsOfTenantQuery(String tenantId);

  /**
   * Builds the document of a key.
   *
   * @param sourceDocuments source documents having the key, not empty
   * @return document fields, or empty if the key has no document
   */
  Optional<Map<String, Object>> build(List<Map<String, Object>> sourceDocuments);
}
