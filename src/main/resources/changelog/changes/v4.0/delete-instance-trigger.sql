CREATE OR REPLACE FUNCTION delete_instance_deps_trigger()
RETURNS TRIGGER AS $$
DECLARE
  i jsonb;
BEGIN
  -- delete classifications
  UPDATE classification SET instances = new_json.new_arr
  FROM (
    -- select all except the one with instances.instanceId = OLD.id
    SELECT id, number, typeId, json_agg(element) AS new_arr
    FROM classification,
      jsonb_array_elements(instances) AS element
    WHERE element ->> 'instanceId' != OLD.id
  ) new_json
  WHERE new_json.number = classification.number AND new_json.typeId = classification.typeId;

  -- delete subjects
  UPDATE subject SET instances = new_json.new_arr
  FROM (
    -- select all except the one with instances.instanceId = OLD.id
    SELECT id, value, authorityId, json_agg(element) AS new_arr
    FROM subject,
      jsonb_array_elements(instances) AS element
    WHERE element ->> 'instanceId' != OLD.id
  ) new_json
  WHERE new_json.authorityId = subject.authorityId AND new_json.value = subject.value;

  -- delete contributors
  UPDATE contributor SET instances = new_json.new_arr
  FROM (
    -- select all except the one with instances.instanceId = OLD.id
    SELECT id, name, contributorNameTypeId, authorityId, json_agg(element) AS new_arr
    FROM contributor,
      jsonb_array_elements(instances) AS element
    WHERE element ->> 'instanceId' != OLD.id
  ) new_json
  WHERE new_json.name = contributor.name AND new_json.authorityId = contributor.authorityId AND new_json.contributorNameTypeId = contributor.contributorNameTypeId
  RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS instance_deleted_trigger ON instance CASCADE;
CREATE TRIGGER instance_deleted_trigger
  AFTER DELETE
  ON instance
  FOR EACH ROW
  EXECUTE FUNCTION delete_instance_deps_trigger();
