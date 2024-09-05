CREATE OR REPLACE FUNCTION check_merge_completed_status()
    RETURNS BOOLEAN AS
$$
DECLARE
    result BOOLEAN;
BEGIN
    SELECT count(1) = 3
    INTO result
    FROM reindex_status
    WHERE entity_type IN ('ITEM', 'INSTANCE', 'HOLDINGS')
      AND status = 'MERGE_COMPLETED';

    RETURN result;
END;
$$ LANGUAGE plpgsql;