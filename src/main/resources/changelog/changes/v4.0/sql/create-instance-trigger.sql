CREATE FUNCTION coalesce_to_empty(value text) RETURNS text AS
$$
BEGIN
    RETURN coalesce(value, '');
END
$$ LANGUAGE plpgsql;

CREATE FUNCTION prepare_for_expected_format(value text, length integer) RETURNS text AS
$$
BEGIN
    RETURN substring(replace(coalesce_to_empty(value), '\', '\\') FROM 1 FOR length);
END
$$ LANGUAGE plpgsql;

CREATE FUNCTION calculate_hash_id(elements text[]) RETURNS text AS
$$
BEGIN
    RETURN encode(digest(array_to_string(elements, '|')::bytea, 'sha1'), 'hex');
END
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION instance_deps_trigger()
    RETURNS trigger AS
$$
DECLARE
    entry                    jsonb;
    classification_id        text;
    classification_number    text;
    classification_type_id   text;
    subject_id               text;
    subject_value            text;
    subject_authority_id     text;
    contributor_id           text;
    contributor_name         text;
    contributor_authority_id text;
    contributor_name_type_id text;
BEGIN
    -- process classifications
    IF TG_OP <> 'INSERT' THEN
        DELETE
        FROM instance_classification
        WHERE instance_id = NEW.id;
    END IF;

    IF TG_OP <> 'DELETE' THEN
        FOR entry IN SELECT * FROM jsonb_array_elements(NEW.json -> 'classifications')
            LOOP
                classification_number := prepare_for_expected_format(entry ->> 'classificationNumber', 50);
                classification_type_id := entry ->> 'classificationTypeId';
                classification_id := calculate_hash_id(ARRAY [classification_number,
                    coalesce_to_empty(classification_type_id)]);

                INSERT
                INTO classification(id, number, type_id)
                VALUES (classification_id, classification_number, classification_type_id)
                ON CONFLICT (id) DO NOTHING;

                INSERT
                INTO instance_classification(instance_id, classification_id, tenant_id, shared)
                VALUES (NEW.id, classification_id, NEW.tenant_id, NEW.shared);
            END LOOP;
    END IF;

    -- process subjects
    IF TG_OP <> 'INSERT' THEN
        DELETE
        FROM instance_subject
        WHERE instance_id = NEW.id;
    END IF;

    IF TG_OP <> 'DELETE' THEN
        FOR entry IN SELECT * FROM jsonb_array_elements(NEW.json -> 'subjects')
            LOOP
                subject_value := prepare_for_expected_format(entry ->> 'value', 255);
                subject_authority_id := entry ->> 'authorityId';
                subject_id := calculate_hash_id(ARRAY [subject_value,
                    coalesce_to_empty(subject_authority_id)]);

                INSERT
                INTO subject(id, value, authority_id)
                VALUES (subject_id, subject_value, subject_authority_id)
                ON CONFLICT (id) DO NOTHING;

                INSERT
                INTO instance_subject(instance_id, subject_id, tenant_id, shared)
                VALUES (NEW.id, subject_id, NEW.tenant_id, NEW.shared);
            END LOOP;
    END IF;

    -- process contributors
    IF TG_OP <> 'INSERT' THEN
        DELETE
        FROM instance_contributor
        WHERE instance_id = NEW.id;
    END IF;

    IF TG_OP <> 'DELETE' THEN
        FOR entry IN SELECT * FROM jsonb_array_elements(NEW.json -> 'contributors')
            LOOP
                contributor_name := prepare_for_expected_format(entry ->> 'name', 255);
                contributor_name_type_id := entry ->> 'contributorNameTypeId';
                contributor_authority_id := entry ->> 'authorityId';
                contributor_id := calculate_hash_id(ARRAY [contributor_name,
                    coalesce_to_empty(contributor_name_type_id),
                    coalesce_to_empty(contributor_authority_id)]);

                INSERT
                INTO contributor(id, name, name_type_id, authority_id)
                VALUES (contributor_id, contributor_name, contributor_name_type_id, contributor_authority_id)
                ON CONFLICT (id) DO NOTHING;

                INSERT
                INTO instance_contributor(instance_id, contributor_id, type_id, tenant_id, shared)
                VALUES (NEW.id, contributor_id, coalesce_to_empty(entry ->> 'contributorTypeId'),
                        NEW.tenant_id, NEW.shared);
            END LOOP;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS instance_trigger ON instance CASCADE;
CREATE TRIGGER instance_trigger
    AFTER INSERT OR UPDATE OR DELETE
    ON instance
    FOR EACH ROW
EXECUTE FUNCTION instance_deps_trigger();
