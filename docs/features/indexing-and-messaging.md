---
feature_id: indexing-and-messaging
title: Indexing and Messaging
updated: 2026-05-22
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
- Browse config data deletion events evict the reference-data cache.
- All consumers retry on failure; retry behaviour is configured per-listener.

## Business rules and constraints

### Event processing pipeline
- Inventory instance/holdings/item/bound-with events are first consumed from the inventory topic, mapped to `IndexInstanceEvent`, and re-published to `search.index.instance` for deduplication and ordered processing.
- Holdings, items, and bound-with events are resolved to their parent instance and trigger a full instance re-index.
- Authority events sourced from consortium shadow copies are filtered out before indexing.
- Browse config data deletion events evict the `reference-data-cache`.

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
| `INDEXING_DATA_FORMAT` | Serialisation format for indexed documents (`smile` or `json`) |
| `INSTANCE_CHILDREN_INDEX_ENABLED` | Enables scheduled sub-resource (holdings/items) indexing |
| `INSTANCE_CHILDREN_INDEX_DELAY_MS` | Fixed delay between sub-resource indexing cycles |
| `SUB_RESOURCE_BATCH_SIZE` | Batch size for sub-resource indexing |
| `STALE_LOCK_THRESHOLD_MS` | Threshold after which a sub-resource processing lock is considered stale |

## Dependencies and interactions
- Depends on Elasticsearch/OpenSearch for index write operations.
- Consumes events from mod-inventory-storage, mod-authorities, and mod-linked-data via Kafka.
