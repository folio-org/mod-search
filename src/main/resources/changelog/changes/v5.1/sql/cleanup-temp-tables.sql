DO
$$
    DECLARE
        temp_table record;
    BEGIN
        -- Loop through all temp tables associated with deprecated jobs
        FOR temp_table IN SELECT temp_table_name
                               FROM resource_ids_job
                               WHERE status = 'DEPRECATED'
            LOOP
                RAISE NOTICE 'Dropping temp table %', temp_table.temp_table_name;
                EXECUTE format('DROP TABLE IF EXISTS %I', temp_table.temp_table_name);
            END LOOP;
    END
$$;
