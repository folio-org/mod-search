---
feature_id: indexing-and-messaging
title: Indexing and Messaging
updated: 2026-10-23
---

# Indexing and Messaging

## What it does
Keeps the Elasticsearch index up to date by consuming Kafka change events from the inventory and other FOLIO modules, transforming them into index operations, and applying them to the appropriate tenant index. Also exposes a REST API for manually submitting records for indexing and for managing index mappings and settings.

## Why it exists
Search results are only as current as the index. This feature provides the real-time data pipeline that propagates creates, updates, and deletes from source-of-truth modules (mod-inventory-storage, mod-authorities, mod-linked-data) into the search index with minimal lag.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| POST | /search/index/records | Manually submit resource events for indexing |
| PUT | /search/index/mappings | Update Elasticsearch index mappings |
| PUT | /search/index/settings | Update Elasticsearch index settings |
| POST | /search/index/indices | Create indices for a tenant |

| Type | Topic pattern (from config) | Description |
|------|-----------------------------|-------------|
| Kafka Consumer | `folio.kafka.listener.events.topicPattern` | Inventory instance/holding/item/bound-with change events; mapped to `search.index.instance` topic for deduplication |
| Kafka Consumer | `folio.kafka.listener.index-instance.topicPattern` | Internal `search.index.instance` events; triggers actual instance indexing |
| Kafka Consumer | `folio.kafka.listener.authorities.topicPattern` | Authority record change events |
| Kafka Consumer | `folio.kafka.listener.linked-data.topicPattern` | Linked data instance/work/hub change events |
| Kafka Consumer | `folio.kafka.listener.location.topicPattern` | Location/campus/institution/library reference data events |
| Kafka Consumer | `folio.kafka.listener.browse-config-data.topicPattern` | Call-number-type and classification-type config events |

### Event processing
- Instance inventory events are first consumed from the inventory topic, mapped to `IndexInstanceEvent`, and re-published to `search.index.instance` for deduplication and ordered processing.
- Holdings, items, and bound-with events are also handled in the same events listener and resolved to their parent instance for re-indexing.
- Authority events sourced from consortium shadow copies are filtered before indexing.
- When `ITEM_INDEX_ENABLED=true`, item events from the same listener are additionally indexed as standalone documents into the `item` index; see [Standalone item indexing](#standalone-item-indexing).
- Browse config data deletion events evict the reference-data cache.
- All consumers retry on failure; retry behaviour is configured per-listener.

## Business rules and constraints

### Event processing pipeline
- Inventory instance/holdings/item/bound-with events are first consumed from the inventory topic, mapped to `IndexInstanceEvent`, and re-published to `search.index.instance` for deduplication and ordered processing.
- Holdings, items, and bound-with events are resolved to their parent instance and trigger a full instance re-index.
- Authority events sourced from consortium shadow copies are filtered out before indexing.
- Browse config data deletion events evict the `reference-data-cache`.

### Standalone item indexing
- Disabled by default; enabled with `ITEM_INDEX_ENABLED=true` (`folio.search-config.indexing.item-index-enabled`). When disabled, the `ItemIndexService` bean is not created and item events are handled only by the instance pipeline.
- When enabled, `inventory.item` events consumed by the inventory events listener are, in addition to the instance flow (which is unchanged), indexed as standalone documents into the `item` index used by [Item Search](item-search.md):
  - create/update upserts the item document, delete removes it;
  - a delete-all event removes all item documents of the tenant (delete-by-query on `tenantId`).
- Item events submitted through `POST /search/index/records` are indexed the same way, in addition to the instance flow. The endpoint response reports an error if any item event failed (failed events are stored as described below).
- `ItemIndexService` indexes copies of the events, so the instance flow is not affected by item-specific changes to the events.
- The document id is `<itemId>|<tenant>`, so the same item from different consortium tenants (e.g. after an ownership move) can coexist in the central index; the `id` field stays the plain item UUID.
- Nested item properties are flattened by `ItemEventPreProcessor` (`status.name` -> `statusName`, `effectiveCallNumberComponents.*` -> `callNumber`, `callNumberPrefix`, `callNumberSuffix`, `callNumberTypeId`); the remaining fields are copied according to `model/item.json`.
- Reindex (full/upload) does not populate the item index yet: only events received while the flag is enabled are indexed.

### Delivery guarantees
- Producer is configured with `acks=all`, idempotence enabled, and up to 5 retries to ensure at-least-once delivery.
- Consumer batch size is controlled by `KAFKA_CONSUMER_MAX_POLL_RECORDS`.

### Sub-resource indexing
- Instance sub-resources (holdings, items) are indexed on a fixed scheduled delay after the parent instance event (`INSTANCE_CHILDREN_INDEX_DELAY_MS`).
- Sub-resource processing uses a distributed lock to prevent concurrent processing across clustered nodes. Stale locks older than `STALE_LOCK_THRESHOLD_MS` are released automatically.

### Data format
- Index data is serialised in Smile binary format by default (`INDEXING_DATA_FORMAT`).

## Error behavior
- Failed individual events are logged with resource name, event type, tenant, and ID; the batch continues processing.
- Kafka consumer retries are configured via `KAFKA_RETRY_INTERVAL_MS` and `KAFKA_RETRY_DELIVERY_ATTEMPTS`.

### Standalone item indexing failures
- Rejected item documents (including partial bulk failures) and failed delete-all requests raise an `IndexingFailureException` carrying the OpenSearch HTTP status and error type (e.g. `mapper_parsing_exception`).
- Item indexing uses its own retry template (`itemIndexingRetryTemplate`) with the Kafka retry settings, except that client errors (4xx other than 429) are not retried, since they fail the same way on every attempt. A batch that fails is retried and then processed event by event, so only events that really fail are reported.
- Events that still fail are logged and stored in the tenant's `failed_indexing_event` table.

### Failed indexing event storage
- `failed_indexing_event` (one per tenant schema) stores: resource type, resource id, event type, the event payload (JSONB, as received, so it can be replayed), the full failure message (whole cause chain, including index, document id and nested OpenSearch `caused_by` details), the HTTP `status` and OpenSearch `error_type` when known, and the created date.
- The table is generic: currently only standalone item indexing writes to it, other record types can be added later.
- A storage failure is only logged and never breaks event processing.
- A daily scheduler (00:30) deletes records older than `FAILED_INDEXING_EVENTS_RETENTION_DAYS` days (default 30) in every tenant.

## Configuration
| Variable | Purpose |
|----------|---------|
| `KAFKA_HOST` / `KAFKA_PORT` | Kafka broker address |
| `KAFKA_CONSUMER_MAX_POLL_RECORDS` | Maximum records per Kafka consumer poll |
| `KAFKA_EVENTS_CONCURRENCY` | Concurrency for inventory events consumer |
| `KAFKA_EVENTS_CONSUMER_PATTERN` | Topic pattern for inventory change events |
| `KAFKA_AUTHORITIES_CONCURRENCY` | Concurrency for authority events consumer |
| `KAFKA_LINKED_DATA_CONCURRENCY` | Concurrency for linked data events consumer |
| `KAFKA_INDEX_INSTANCE_CONCURRENCY` | Concurrency for index-instance internal topic consumer |
| `KAFKA_RETRY_INTERVAL_MS` | Retry interval for failed Kafka deliveries |
| `KAFKA_RETRY_DELIVERY_ATTEMPTS` | Number of delivery retry attempts |
| `ITEM_INDEX_ENABLED` | Enables indexing of item events into the `item` index (default `false`) |
| `FAILED_INDEXING_EVENTS_RETENTION_DAYS` | Retention in days for stored failed indexing events (default `30`) |
| `INDEXING_DATA_FORMAT` | Serialisation format for indexed documents (`smile` or `json`) |
| `INSTANCE_CHILDREN_INDEX_ENABLED` | Enables scheduled sub-resource (holdings/items) indexing |
| `INSTANCE_CHILDREN_INDEX_DELAY_MS` | Fixed delay between sub-resource indexing cycles |
| `SUB_RESOURCE_BATCH_SIZE` | Batch size for sub-resource indexing |
| `STALE_LOCK_THRESHOLD_MS` | Threshold after which a sub-resource processing lock is considered stale |

## Dependencies and interactions
- Depends on Elasticsearch/OpenSearch for index write operations.
- Consumes events from mod-inventory-storage, mod-authorities, and mod-linked-data via Kafka.
