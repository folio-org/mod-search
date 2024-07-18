CREATE OR REPLACE FUNCTION delete_instance_deps_trigger()
RETURNS TRIGGER AS $$
DECLARE
  i jsonb;
BEGIN
  -- delete classifications
  UPDATE classifications SET instances_arr = new_arr.new_arr
  FROM (
    -- select all except the one with instances_arr.instanceId = OLD.instance_id
    SELECT classificationNumber, classificationTypeId, json_agg(element) AS new_arr
    FROM classifications,
      jsonb_array_elements(instances_arr) AS element
    WHERE element ->> 'instanceId' != OLD.instance_id
  ) new_arr
  WHERE new_arr.classificationNumber = classifications.classificationNumber AND new_arr.classificationTypeId = classifications.classificationTypeId;

  -- delete subjects
  UPDATE subjects SET instances_arr = new_arr.new_arr
  FROM (
    -- select all except the one with instances_arr.instanceId = OLD.instance_id
    SELECT subject_id, subject_value, json_agg(element) AS new_arr
    FROM subjects,
      jsonb_array_elements(instances_arr) AS element
    WHERE element ->> 'instanceId' != OLD.instance_id
  ) new_arr
  WHERE new_arr.subject_id = subjects.subject_id AND new_arr.subject_value = subjects.subject_value;

  -- delete contributors
  UPDATE contributors SET instances_arr = new_arr.new_arr
  FROM (
    -- select all except the one with instances_arr.instanceId = OLD.instance_id
    SELECT contributor_id, contributor_name, json_agg(element) AS new_arr
    FROM contributors,
      jsonb_array_elements(instances_arr) AS element
    WHERE element ->> 'instanceId' != OLD.instance_id
  ) new_arr
  WHERE new_arr.contributor_id = contributors.contributor_id AND new_arr.contributor_name = contributors.contributor_name;
  RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS instance_deleted_trigger ON instances CASCADE;
CREATE TRIGGER instance_deleted_trigger
  AFTER DELETE
  ON instances
  FOR EACH ROW
  EXECUTE FUNCTION delete_instance_deps_trigger();
