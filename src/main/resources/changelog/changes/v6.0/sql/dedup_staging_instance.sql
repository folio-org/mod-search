CREATE OR REPLACE FUNCTION dedup_staging_instance()
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
            id, tenant_id, shared, is_bound_with, json, inserted_at,
            COUNT(*) OVER () AS total_records,
            ROW_NUMBER() OVER (PARTITION BY id ORDER BY inserted_at DESC) as rn
        FROM staging_instance
    ),
    deduped AS (
        SELECT
            id, tenant_id, shared, is_bound_with, json,
            MAX(total_records) AS total_records
        FROM staging_with_stats
        WHERE rn = 1
        GROUP BY id, tenant_id, shared, is_bound_with, json
    ),
    inserted AS (
        INSERT INTO instance (id, tenant_id, shared, is_bound_with, json)
        SELECT id, tenant_id, shared, is_bound_with, json FROM deduped
        ON CONFLICT (id) DO UPDATE SET
            tenant_id = EXCLUDED.tenant_id,
            shared = EXCLUDED.shared,
            is_bound_with = EXCLUDED.is_bound_with,
            json = EXCLUDED.json
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
