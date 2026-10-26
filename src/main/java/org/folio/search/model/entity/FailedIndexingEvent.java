package org.folio.search.model.entity;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Resource event that failed to be indexed, stored for troubleshooting.
 *
 * @param id           unique identifier of the record
 * @param resourceType name of the resource type, e.g. {@code item}
 * @param resourceId   id of the resource from the event
 * @param eventType    type of the event, e.g. {@code CREATE}
 * @param event        serialized event as JSON
 * @param errorMessage reason of the failure, including the whole cause chain
 * @param status       HTTP status of the failure reported by OpenSearch, nullable
 * @param errorType    OpenSearch error type, e.g. {@code mapper_parsing_exception}, nullable
 * @param createdDate  date when the failure was stored
 */
public record FailedIndexingEvent(UUID id, String resourceType, String resourceId, String eventType, String event,
                                  String errorMessage, Integer status, String errorType, Timestamp createdDate) { }
