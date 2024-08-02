CREATE OR REPLACE FUNCTION instance_deps_trigger()
RETURNS TRIGGER AS $$
DECLARE
  i jsonb;
BEGIN
  -- extract classifications
  FOR i IN SELECT * FROM jsonb_array_elements(NEW.instance_json -> 'classifications') LOOP
    INSERT
    INTO classification(id, number, type_id, instances)

    VALUES (encode(digest((concat_ws('|', coalesce(i ->> 'classificationNumber',''), coalesce(i ->> 'classificationTypeId','')))::bytea, 'sha1'), 'hex'),
            i ->> 'classificationNumber',
            i ->> 'classificationTypeId',
            jsonb_build_array(
              json_build_object(
                'instanceId', NEW.id,
                'shared', NEW.shared,
                'tenantId', NEW.tenant_id
              )
            ))
    ON CONFLICT (id)
      DO UPDATE SET instances = classification.instances || excluded.instances;
  END LOOP;

  -- extract subjects
  FOR i IN SELECT * FROM jsonb_array_elements(NEW.instance_json -> 'subjects') LOOP
    INSERT
    INTO subject(id, value, authority_id, instances)
    VALUES (encode(digest((concat_ws('|', coalesce(i ->> 'value',''), coalesce(i ->> 'authorityId','')))::bytea, 'sha1'), 'hex'), -- requires pgcrypto enabled
            i ->> 'value',
            i ->> 'authorityId',
            jsonb_build_array(
              json_build_object(
                'instanceId', NEW.id,
                'shared', NEW.shared,
                'tenantId', NEW.tenant_id
              )
            ))
    ON CONFLICT (id)
      DO UPDATE SET instances = subject.instances || excluded.instances;
  END LOOP;

  -- extract contributors
  FOR i IN SELECT * FROM jsonb_array_elements(NEW.instance_json -> 'contributors') LOOP
    INSERT
    INTO contributor(id, name, contributor_name_type_id, authority_id, instances)
    VALUES (encode(digest((concat_ws('|', coalesce(i ->> 'name',''), coalesce(i ->> 'contributorNameTypeId',''), coalesce(i ->> 'authorityId','')))::bytea, 'sha1'), 'hex'), -- requires pgcrypto enabled
            i ->> 'name' ,
            i ->> 'contributorNameTypeId',
            i ->> 'authorityId',
            jsonb_build_array(
              json_build_object(
                'instanceId', NEW.id,
                'shared', NEW.shared,
                'tenantId', NEW.tenant_id,
                'contributorTypeId', i ->> 'contributorTypeId'
              )
            ))
    ON CONFLICT (id)
      DO UPDATE SET instances = contributor.instances || excluded.instances;
  END LOOP;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS instance_trigger ON instance CASCADE;
CREATE TRIGGER instance_trigger
  AFTER INSERT OR UPDATE
  ON instance
  FOR EACH ROW
  EXECUTE FUNCTION instance_deps_trigger();
