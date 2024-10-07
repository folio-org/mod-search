CREATE OR REPLACE FUNCTION coalesce_to_empty(value text) RETURNS text AS
$$
BEGIN
    RETURN coalesce(value, '');
END
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION prepare_for_expected_format(value text, length integer) RETURNS text AS
$$
BEGIN
    RETURN substring(replace(coalesce_to_empty(value), '\', '\\') FROM 1 FOR length);
END
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION calculate_hash_id(elements text[]) RETURNS text AS
$$
BEGIN
    RETURN encode(digest(array_to_string(elements, '|')::bytea, 'sha1'), 'hex');
END
$$ LANGUAGE plpgsql;

CREATE TYPE text_pair AS
(
    first_text  text,
    second_text text
);

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
    subject_source_id     text;
    subject_type_id     text;
    contributor_id           text;
    contributor_name         text;
    contributor_authority_id text;
    contributor_name_type_id text;
    classification_arr       classification[];
    classification_id_arr    text[];
    subject_arr              subject[];
    subject_id_arr           text[];
    contributor_arr          contributor[];
    contributor_id_arr       text_pair[];
BEGIN
    -- process classifications
    IF TG_OP <> 'INSERT' THEN
        DELETE
        FROM instance_classification
        WHERE instance_id = OLD.id;
    END IF;

    IF TG_OP <> 'DELETE' THEN
        FOR entry IN SELECT * FROM jsonb_array_elements(NEW.json -> 'classifications')
            LOOP
                classification_number := prepare_for_expected_format(entry ->> 'classificationNumber', 50);
                IF classification_number = '' OR classification_number = ' ' THEN
                    CONTINUE;
                END IF;
                classification_type_id := entry ->> 'classificationTypeId';
                classification_id := calculate_hash_id(ARRAY [classification_number,
                    coalesce_to_empty(classification_type_id)]);

                classification_arr := array_append(classification_arr,
                                                   ROW (classification_id, classification_number, classification_type_id)::classification);
                classification_id_arr := array_append(classification_id_arr, classification_id);
            END LOOP;

        INSERT
        INTO classification(id, number, type_id)
        SELECT id, number, type_id
        FROM unnest(classification_arr)
        ORDER BY id, number, type_id
        ON CONFLICT DO NOTHING;

        INSERT
        INTO instance_classification(instance_id, classification_id, tenant_id, shared)
        SELECT NEW.id, cid, NEW.tenant_id, NEW.shared
        FROM unnest(classification_id_arr) cid
        ORDER BY cid
        ON CONFLICT DO NOTHING;
    END IF;

    -- process subjects
    IF TG_OP <> 'INSERT' THEN
        DELETE
        FROM instance_subject
        WHERE instance_id = OLD.id;
    END IF;

    IF TG_OP <> 'DELETE' THEN
        FOR entry IN SELECT * FROM jsonb_array_elements(NEW.json -> 'subjects')
            LOOP
                subject_value := prepare_for_expected_format(entry ->> 'value', 255);
                IF subject_value = '' OR subject_value = ' ' THEN
                    CONTINUE;
                END IF;
                subject_authority_id := entry ->> 'authorityId';
                subject_source_id := entry ->> 'sourceId';
                subject_type_id := entry ->> 'typeId';
                subject_id := calculate_hash_id(ARRAY [subject_value, coalesce_to_empty(subject_authority_id), coalesce_to_empty(subject_source_id), coalesce_to_empty(subject_type_id)]);

                subject_arr := array_append(subject_arr,
                                            ROW (subject_id, subject_value, subject_authority_id, subject_source_id, subject_type_id)::subject);
                subject_id_arr := array_append(subject_id_arr, subject_id);
            END LOOP;

        INSERT
        INTO subject(id, value, authority_id, source_id, type_id)
        SELECT id, value, authority_id, source_id, type_id
        FROM unnest(subject_arr)
        ORDER BY id, value, authority_id, source_id, type_id
        ON CONFLICT DO NOTHING;

        INSERT
        INTO instance_subject(instance_id, subject_id, tenant_id, shared)
        SELECT NEW.id, sid, NEW.tenant_id, NEW.shared
        FROM unnest(subject_id_arr) sid
        ORDER BY sid
        ON CONFLICT DO NOTHING;
    END IF;


    -- process contributors
    IF TG_OP <> 'INSERT' THEN
        DELETE
        FROM instance_contributor
        WHERE instance_id = OLD.id;
    END IF;

    IF TG_OP <> 'DELETE' THEN
        FOR entry IN SELECT * FROM jsonb_array_elements(NEW.json -> 'contributors')
            LOOP
                contributor_name := prepare_for_expected_format(entry ->> 'name', 255);
                IF contributor_name = '' OR contributor_name = ' ' THEN
                    CONTINUE;
                END IF;
                contributor_name_type_id := entry ->> 'contributorNameTypeId';
                contributor_authority_id := entry ->> 'authorityId';
                contributor_id := calculate_hash_id(ARRAY [contributor_name,
                    coalesce_to_empty(contributor_name_type_id),
                    coalesce_to_empty(contributor_authority_id)]);

                contributor_arr := array_append(contributor_arr,
                                                ROW (contributor_id, contributor_name,
                                                    contributor_name_type_id, contributor_authority_id)::contributor);
                contributor_id_arr := array_append(contributor_id_arr,
                                                   ROW (contributor_id,
                                                       coalesce_to_empty(entry ->> 'contributorTypeId'))::text_pair);
            END LOOP;

        INSERT
        INTO contributor(id, name, name_type_id, authority_id)
        SELECT id, name, name_type_id, authority_id
        FROM unnest(contributor_arr)
        ORDER BY id, name, name_type_id, authority_id
        ON CONFLICT DO NOTHING;

        INSERT
        INTO instance_contributor(instance_id, contributor_id, type_id, tenant_id, shared)
        SELECT NEW.id, cid.first_text, cid.second_text, NEW.tenant_id, NEW.shared
        FROM unnest(contributor_id_arr) cid
        ORDER BY cid
        ON CONFLICT DO NOTHING;
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
