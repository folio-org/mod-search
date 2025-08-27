-- Function to cleanup all staging tables
CREATE OR REPLACE FUNCTION cleanup_all_staging_tables() 
RETURNS void
SET search_path FROM CURRENT
AS $$
BEGIN
    -- Truncate all staging tables
    TRUNCATE TABLE staging_instance CASCADE;
    TRUNCATE TABLE staging_holding CASCADE;
    TRUNCATE TABLE staging_item CASCADE;
    TRUNCATE TABLE staging_instance_subject CASCADE;
    TRUNCATE TABLE staging_instance_contributor CASCADE;
    TRUNCATE TABLE staging_instance_classification CASCADE;
    TRUNCATE TABLE staging_instance_call_number CASCADE;
    
    RAISE NOTICE 'All staging tables have been truncated successfully';
END;
$$ LANGUAGE plpgsql;

-- Function to get staging table statistics
CREATE OR REPLACE FUNCTION get_staging_table_stats() 
RETURNS TABLE (
    table_name TEXT,
    record_count BIGINT
)
SET search_path FROM CURRENT
AS $$
BEGIN
    RETURN QUERY
    SELECT 'staging_instance'::TEXT, COUNT(*) FROM staging_instance
    UNION ALL
    SELECT 'staging_holding'::TEXT, COUNT(*) FROM staging_holding
    UNION ALL
    SELECT 'staging_item'::TEXT, COUNT(*) FROM staging_item
    UNION ALL
    SELECT 'staging_instance_subject'::TEXT, COUNT(*) FROM staging_instance_subject
    UNION ALL
    SELECT 'staging_instance_contributor'::TEXT, COUNT(*) FROM staging_instance_contributor
    UNION ALL
    SELECT 'staging_instance_classification'::TEXT, COUNT(*) FROM staging_instance_classification
    UNION ALL
    SELECT 'staging_instance_call_number'::TEXT, COUNT(*) FROM staging_instance_call_number;
END;
$$ LANGUAGE plpgsql;