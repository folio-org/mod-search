-- Function to deduplicate staging_instance_subject
CREATE OR REPLACE FUNCTION dedup_staging_instance_subject()
RETURNS TABLE (
    total_staging_records BIGINT,
    duplicates_removed BIGINT,
    records_upserted BIGINT
)
SET search_path FROM CURRENT
AS $$
DECLARE
    v_total_staging BIGINT;
    v_upserted BIGINT;
BEGIN
    -- Single scan: collect statistics and perform deduplication in one pass
    WITH staging_with_stats AS (
        SELECT
            instance_id, subject_id, tenant_id, shared, inserted_at,
            COUNT(*) OVER () AS total_records,
            ROW_NUMBER() OVER (PARTITION BY subject_id, instance_id, tenant_id ORDER BY inserted_at DESC) as rn
        FROM staging_instance_subject
    ),
    deduped AS (
        SELECT
            instance_id, subject_id, tenant_id, shared,
            MAX(total_records) AS total_records
        FROM staging_with_stats
        WHERE rn = 1
        GROUP BY instance_id, subject_id, tenant_id, shared
    ),
    inserted AS (
        INSERT INTO instance_subject (instance_id, subject_id, tenant_id, shared)
        SELECT instance_id, subject_id, tenant_id, shared FROM deduped
        ON CONFLICT (subject_id, instance_id, tenant_id) DO UPDATE SET
            shared = EXCLUDED.shared
        RETURNING 1
    )
    SELECT
        MAX(total_records),
        (SELECT COUNT(*) FROM inserted)
    INTO v_total_staging, v_upserted
    FROM deduped;

    RETURN QUERY SELECT
        v_total_staging,
        v_total_staging,
        v_upserted;
END;
$$ LANGUAGE plpgsql;

-- Function to deduplicate staging_instance_contributor
CREATE OR REPLACE FUNCTION dedup_staging_instance_contributor()
RETURNS TABLE (
    total_staging_records BIGINT,
    duplicates_removed BIGINT,
    records_upserted BIGINT
)
SET search_path FROM CURRENT
AS $$
DECLARE
    v_total_staging BIGINT;
    v_upserted BIGINT;
BEGIN
    -- Single scan: collect statistics and perform deduplication in one pass
    WITH staging_with_stats AS (
        SELECT
            instance_id, contributor_id, type_id, tenant_id, shared, inserted_at,
            COUNT(*) OVER () AS total_records,
            ROW_NUMBER() OVER (PARTITION BY contributor_id, instance_id, type_id, tenant_id ORDER BY inserted_at DESC) as rn
        FROM staging_instance_contributor
    ),
    deduped AS (
        SELECT
            instance_id, contributor_id, type_id, tenant_id, shared,
            MAX(total_records) AS total_records
        FROM staging_with_stats
        WHERE rn = 1
        GROUP BY instance_id, contributor_id, type_id, tenant_id, shared
    ),
    inserted AS (
        INSERT INTO instance_contributor (instance_id, contributor_id, type_id, tenant_id, shared)
        SELECT instance_id, contributor_id, type_id, tenant_id, shared FROM deduped
        ON CONFLICT (contributor_id, instance_id, type_id, tenant_id) DO UPDATE SET
            shared = EXCLUDED.shared
        RETURNING 1
    )
    SELECT
        MAX(total_records),
        (SELECT COUNT(*) FROM inserted)
    INTO v_total_staging, v_upserted
    FROM deduped;

    RETURN QUERY SELECT
        v_total_staging,
        v_total_staging,
        v_upserted;
END;
$$ LANGUAGE plpgsql;

-- Function to deduplicate staging_instance_classification
CREATE OR REPLACE FUNCTION dedup_staging_instance_classification()
RETURNS TABLE (
    total_staging_records BIGINT,
    duplicates_removed BIGINT,
    records_upserted BIGINT
)
SET search_path FROM CURRENT
AS $$
DECLARE
    v_total_staging BIGINT;
    v_upserted BIGINT;
BEGIN
    -- Single scan: collect statistics and perform deduplication in one pass
    WITH staging_with_stats AS (
        SELECT
            instance_id, classification_id, tenant_id, shared, inserted_at,
            COUNT(*) OVER () AS total_records,
            ROW_NUMBER() OVER (PARTITION BY classification_id, instance_id, tenant_id ORDER BY inserted_at DESC) as rn
        FROM staging_instance_classification
    ),
    deduped AS (
        SELECT
            instance_id, classification_id, tenant_id, shared,
            MAX(total_records) AS total_records
        FROM staging_with_stats
        WHERE rn = 1
        GROUP BY instance_id, classification_id, tenant_id, shared
    ),
    inserted AS (
        INSERT INTO instance_classification (instance_id, classification_id, tenant_id, shared)
        SELECT instance_id, classification_id, tenant_id, shared FROM deduped
        ON CONFLICT (classification_id, instance_id, tenant_id) DO UPDATE SET
            shared = EXCLUDED.shared
        RETURNING 1
    )
    SELECT
        MAX(total_records),
        (SELECT COUNT(*) FROM inserted)
    INTO v_total_staging, v_upserted
    FROM deduped;

    RETURN QUERY SELECT
        v_total_staging,
        v_total_staging,
        v_upserted;
END;
$$ LANGUAGE plpgsql;

-- Function to deduplicate staging_instance_call_number
CREATE OR REPLACE FUNCTION dedup_staging_instance_call_number()
RETURNS TABLE (
    total_staging_records BIGINT,
    duplicates_removed BIGINT,
    records_upserted BIGINT
)
SET search_path FROM CURRENT
AS $$
DECLARE
    v_total_staging BIGINT;
    v_upserted BIGINT;
BEGIN
    -- Single scan: collect statistics and perform deduplication in one pass
    WITH staging_with_stats AS (
        SELECT
            call_number_id, item_id, instance_id, tenant_id, location_id, inserted_at,
            COUNT(*) OVER () AS total_records,
            ROW_NUMBER() OVER (PARTITION BY call_number_id, item_id, instance_id, tenant_id ORDER BY inserted_at DESC) as rn
        FROM staging_instance_call_number
    ),
    deduped AS (
        SELECT
            call_number_id, item_id, instance_id, tenant_id, location_id,
            MAX(total_records) AS total_records
        FROM staging_with_stats
        WHERE rn = 1
        GROUP BY call_number_id, item_id, instance_id, tenant_id, location_id
    ),
    inserted AS (
        INSERT INTO instance_call_number (call_number_id, item_id, instance_id, tenant_id, location_id)
        SELECT call_number_id, item_id, instance_id, tenant_id, location_id FROM deduped
        ON CONFLICT (call_number_id, item_id, instance_id, tenant_id) DO UPDATE SET
            location_id = EXCLUDED.location_id
        RETURNING 1
    )
    SELECT
        MAX(total_records),
        (SELECT COUNT(*) FROM inserted)
    INTO v_total_staging, v_upserted
    FROM deduped;

    RETURN QUERY SELECT
        v_total_staging,
        v_total_staging,
        v_upserted;
END;
$$ LANGUAGE plpgsql;
