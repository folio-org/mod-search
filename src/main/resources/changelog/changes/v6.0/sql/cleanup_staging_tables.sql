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
    TRUNCATE TABLE staging_subject CASCADE;
    TRUNCATE TABLE staging_contributor CASCADE;
    TRUNCATE TABLE staging_classification CASCADE;
    TRUNCATE TABLE staging_call_number CASCADE;

    RAISE NOTICE 'All staging tables have been truncated successfully';
END;
$$ LANGUAGE plpgsql;
