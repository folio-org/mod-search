---
title: Full Reindex — S3 (EXPORT mode)
---

# Full Reindex — S3 (EXPORT mode)

> **Not yet documented.** EXPORT mode (`REINDEX_TYPE=EXPORT`) reads merge records from S3-compatible object storage instead of streaming them over Kafka. A dedicated guide is planned.
>
> In the meantime:
> - The overall flow (merge → upload phases, monitoring, status values) is identical to PUBLISH mode — see the [Reindex overview](../reindex.md).
> - The trigger endpoint, request body, and settings-restore procedure match [Full Reindex — Kafka](reindex-full-kafka.md); only the merge ingestion source differs.
> - EXPORT-specific configuration (`REINDEX_MERGE_EXPORT_BATCH_SIZE`, `KAFKA_REINDEX_FILE_READY_CONCURRENCY`, `REINDEX_S3_*`, `S3_REINDEX_*`) is listed in the [Configuration Reference](../reindex.md#configuration-reference).
> - The merge topic is `inventory.reindex.file-ready` (produced by mod-inventory-storage), consumed with `KAFKA_REINDEX_FILE_READY_CONCURRENCY` (default `4`); the upload phase still uses `search.reindex.range-index`. As in PUBLISH mode, raise a topic's partition count manually with Kafka admin tooling before reindexing — changing an env var does not repartition an existing topic. See [Full Reindex — Kafka › Scaling consumers and partitions](reindex-full-kafka.md#scaling-consumers-and-partitions).

> DRAFT
> S3_REINDEX_MAX_REQUESTS_PER_HOST, S3_REINDEX_MAX_IDLE_CONNECTIONS - controls s3 throughput for both modules
> S3_REINDEX_PART_SIZE_MB - mod-inventory-storage configuration. If set higher than expected file size for a merge range - will upload each file with one write instead of multipart upload increasing performance
<!-- TODO: Document full reindex using EXPORT mode (S3-based merge) -->
