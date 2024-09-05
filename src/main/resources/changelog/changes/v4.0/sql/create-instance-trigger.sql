CREATE OR REPLACE FUNCTION instance_deps_trigger()
    RETURNS TRIGGER AS
$$
DECLARE
    i                     jsonb;
    classification_id     text;
    classification_number text;
    subject_id            text;
    subject_value         text;
    contributor_id        text;
    contributor_name      text;
BEGIN
    -- extract classifications
    DELETE
    FROM instance_classification
    WHERE instance_id = NEW.id;

    FOR i IN SELECT * FROM jsonb_array_elements(NEW.json -> 'classifications')
        LOOP
            classification_number :=
                    substring(replace(coalesce(i ->> 'classificationNumber', ''), '\', '\\') from 1 for 50);
            classification_id := encode(digest((concat_ws('|',
                                                          classification_number,
                                                          coalesce(i ->> 'classificationTypeId', '')
                                                ))::bytea, 'sha1'), 'hex');
            INSERT
            INTO classification(id, number, type_id)
            VALUES (classification_id, classification_number, i ->> 'classificationTypeId')
            ON CONFLICT (id) DO NOTHING;

            INSERT
            INTO instance_classification(instance_id, classification_id, tenant_id, shared)
            VALUES (NEW.id, classification_id, NEW.tenant_id, NEW.shared);
        END LOOP;

    -- extract subjects
    DELETE
    FROM instance_subject
    WHERE instance_id = NEW.id;

    FOR i IN SELECT * FROM jsonb_array_elements(NEW.json -> 'subjects')
        LOOP
            subject_value := substring(replace(coalesce(i ->> 'value', ''), '\', '\\') from 1 for 255);
            subject_id := encode(digest((concat_ws('|',
                                                   subject_value,
                                                   coalesce(i ->> 'authorityId', '')
                                         ))::bytea, 'sha1'), 'hex');
            RAISE notice 'subject: % | instance: %', subject_value, NEW.id;
            INSERT
            INTO subject(id, value, authority_id)
            VALUES (subject_id, subject_value, i ->> 'authorityId')
            ON CONFLICT (id) DO NOTHING;

            INSERT
            INTO instance_subject(instance_id, subject_id, tenant_id, shared)
            VALUES (NEW.id, subject_id, NEW.tenant_id, NEW.shared);
        END LOOP;

    -- extract contributors
    DELETE
    FROM instance_contributor
    WHERE instance_id = NEW.id;

    FOR i IN SELECT * FROM jsonb_array_elements(NEW.json -> 'contributors')
        LOOP
            contributor_name := substring(replace(coalesce(i ->> 'name', ''), '\', '\\') from 1 for 255);
            contributor_id := encode(digest((concat_ws('|',
                                                       contributor_name,
                                                       coalesce(i ->> 'contributorNameTypeId', ''),
                                                       coalesce(i ->> 'authorityId', '')
                                             ))::bytea, 'sha1'), 'hex');
            INSERT
            INTO contributor(id, name, name_type_id, authority_id)
            VALUES (contributor_id, contributor_name, i ->> 'contributorNameTypeId', i ->> 'authorityId')
            ON CONFLICT (id) DO NOTHING;

            INSERT
            INTO instance_contributor(instance_id, contributor_id, type_id, tenant_id, shared)
            VALUES (NEW.id, contributor_id, coalesce(i ->> 'contributorTypeId', ''), NEW.tenant_id, NEW.shared);
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
