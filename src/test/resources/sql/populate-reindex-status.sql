INSERT INTO reindex_status (reindex_id, entity_type, status, total_merge_ranges, processed_merge_ranges, total_upload_ranges, processed_upload_ranges, start_time_merge, end_time_merge, start_time_upload, end_time_upload)
VALUES
    ('180a92d3-6829-44ad-a81c-2cdd61650e4d', 'CONTRIBUTOR', 'MERGE_COMPLETED', '3', '3', '0', '0', '2024-04-01T01:37:34.15755006Z', '2024-04-01T01:37:35.15755006Z', null, null),
    ('180a92d3-6829-44ad-a81c-2cdd61650e4d', 'SUBJECT', 'UPLOAD_IN_PROGRESS', '3', '2', '2', '1', '2024-04-01T01:37:34.15755006Z', '2024-04-01T01:37:35.15755006Z', '2024-04-01T01:37:36.15755006Z', null),
    ('180a92d3-6829-44ad-a81c-2cdd61650e4d', 'INSTANCE', 'UPLOAD_COMPLETED', '3', '3', '2', '2', '2024-04-01T01:37:34.15755006Z', '2024-04-01T01:37:35.15755006Z', '2024-04-01T01:37:36.15755006Z', '2024-04-01T01:37:37.15755006Z'),
    ('180a92d3-6829-44ad-a81c-2cdd61650e4d', 'CLASSIFICATION', 'UPLOAD_FAILED', '3', '3', '2', '1', '2024-04-01T01:37:34.15755006Z', '2024-04-01T01:37:35.15755006Z', '2024-04-01T01:37:36.15755006Z', '2024-04-01T01:37:37.15755006Z');
