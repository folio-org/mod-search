CREATE OR REPLACE FUNCTION update_reindex_status_trigger()
RETURNS TRIGGER AS $$
BEGIN
  -- update status and end time for merge
  IF OLD.status = 'MERGE_IN_PROGRESS' and NEW.total_merge_ranges = NEW.processed_merge_ranges
  THEN NEW.status = 'MERGE_COMPLETED'; NEW.end_time_merge = current_timestamp;
  ELSE
    -- update status and end time for upload
    IF OLD.status = 'UPLOAD_IN_PROGRESS' and NEW.total_upload_ranges = NEW.processed_upload_ranges
    THEN NEW.status = 'UPLOAD_COMPLETED'; NEW.end_time_upload = current_timestamp;
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS reindex_status_updated_trigger ON reindex_status CASCADE;
CREATE TRIGGER reindex_status_updated_trigger
  BEFORE UPDATE OF processed_merge_ranges, processed_upload_ranges
  ON reindex_status
  FOR EACH ROW
  EXECUTE FUNCTION update_reindex_status_trigger();
