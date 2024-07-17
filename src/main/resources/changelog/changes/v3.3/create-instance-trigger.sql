CREATE OR REPLACE FUNCTION instance_deps_trigger()
RETURNS TRIGGER AS $$
DECLARE
  i jsonb;
BEGIN
  -- extract classifications
  FOR i IN SELECT * FROM jsonb_array_elements(NEW.instance_json -> 'classifications') LOOP
    INSERT
    INTO classifications(classification_number, classification_type_id, instances_arr)
    VALUES (i ->> 'classificationNumber',
            i ->> 'classificationTypeId',
            jsonb_build_array(
              json_build_object(
                'instanceId', NEW.instance_id,
                'shared', NEW.shared,
                'tenantId', NEW.tenant_id
              )
            ))
    ON CONFLICT (classification_type_id, classification_number)
      DO UPDATE SET instances_arr = classifications.instances_arr || excluded.instances_arr;
  END LOOP;

  -- extract subjects
  FOR i IN SELECT * FROM jsonb_array_elements(NEW.instance_json -> 'subjects') LOOP
    INSERT
    INTO subjects(subject_id, subject_value, authority_id, instances_arr)
    VALUES (encode(digest((i ->> 'value' || i ->> 'authorityId')::bytea, 'sha1'), 'hex'), -- requires pgcrypto enabled
            i ->> 'value',
            jsonb_build_array(
              json_build_object(
                'instanceId', NEW.instance_id,
                'shared', NEW.shared,
                'tenantId', NEW.tenant_id
              )
            ))
    ON CONFLICT (subject_id, subject_value)
      DO UPDATE SET instances_arr = subjects.instances_arr || excluded.instances_arr;
  END LOOP;

  -- extract contributors
  FOR i IN SELECT * FROM jsonb_array_elements(NEW.instance_json -> 'contributors') LOOP
    INSERT
    INTO contributors(contributor_id, contributor_name, instances_arr)
    VALUES (encode(digest((i ->> 'name' || i ->> 'authorityId')::bytea, 'sha1'), 'hex'), -- requires pgcrypto enabled
            i ->> 'name' ,
            jsonb_build_array(
              json_build_object(
                'instanceId', NEW.instance_id,
                'shared', NEW.shared,
                'tenantId', NEW.tenant_id,
                'contributorTypeId', i ->> 'contributorNameTypeId'
              )
            ))
    ON CONFLICT (contributor_id, contributor_name)
      DO UPDATE SET instances_arr = contributors.instances_arr || excluded.instances_arr;
  END LOOP;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- TODO: Implement trigger on delete
DROP TRIGGER IF EXISTS instance_trigger ON instances CASCADE;
CREATE TRIGGER instance_trigger
  AFTER INSERT OR UPDATE
  ON instances
  FOR EACH ROW
  EXECUTE FUNCTION instance_deps_trigger();
