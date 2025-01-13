-- Insert data into 'call_number' table with some fields set to NULL
INSERT INTO call_number (id, call_number, call_number_prefix, call_number_suffix, call_number_type_id, volume, enumeration, chronology, copy_number)
VALUES
    ('cn1', 'CN-001', 'Pre1', NULL, 'Type1', 'Vol1', 'Enum1', 'Chron1', NULL),
    ('cn2', 'CN-002', NULL, 'Suf2', 'Type2', 'Vol2', NULL, 'Chron2', 'Copy2'),
    ('cn3', 'CN-003', 'Pre3', 'Suf3', NULL, NULL, 'Enum3', NULL, 'Copy3'),
    ('cn4', 'CN-004', NULL, NULL, 'Type4', 'Vol4', 'Enum4', 'Chron4', NULL),
    ('cn5', 'CN-005', 'Pre5', 'Suf5', NULL, 'Vol5', NULL, NULL, 'Copy5'),
    ('cn6', 'CN-006', NULL, 'Suf6', 'Type6', NULL, 'Enum6', 'Chron6', NULL),
    ('cn7', 'CN-007', 'Pre7', NULL, NULL, 'Vol7', 'Enum7', NULL, 'Copy7'),
    ('cn8', 'CN-008', NULL, 'Suf8', 'Type8', NULL, NULL, 'Chron8', 'Copy8'),
    ('cn9', 'CN-009', 'Pre9', 'Suf9', 'Type9', 'Vol9', 'Enum9', NULL, NULL),
    ('cna', 'CN-010', NULL, NULL, NULL, 'Vol10', NULL, 'Chron10', 'Copy10');

-- Insert data into 'instance_call_number' table with varied location and count
INSERT INTO instance_call_number (call_number_id, item_id, instance_id, tenant_id, location_id)
VALUES
    ('cn1', 'b1703fff-6fdb-45d5-bd9e-d5596658a5c5', '9f8febd1-e96c-46c4-a5f4-84a45cc499a2', 'tenant1', 'b0a5b238-243b-4f66-afa6-20b605cc58cb'),
    ('cn1', 'd13e75e0-632e-4d24-b689-a5c7012ddfd7', '9f8febd1-e96c-46c4-a5f4-84a45cc499a2', 'tenant2', '51f4b5b6-74a3-431b-97e1-5e3b1c6b6e9a'),
    ('cn2', 'e4b506e4-8ec3-4f7b-9e5f-8140370e5498', '9f8febd1-e96c-46c4-a5f4-84a45cc499a2', 'tenant1', '731d865a-4719-4c34-893b-09f7069f4ace'),
    ('cn3', 'f1a14562-c929-4b21-86eb-786f4076b728', '9f8febd1-e96c-46c4-a5f4-84a45cc499a2', 'tenant2', 'e89befb2-c947-4b25-b488-d3a1dc0e5032'),
    ('cn3', '0739eca6-23d8-490a-afe4-4886aeb04746', '9f8febd1-e96c-46c4-a5f4-84a45cc499a2', 'tenant1', '4e2f5e0e-a8df-45d0-a036-ef9e90b1e8f3'),
    ('cn4', '4da99244-268f-4027-ad33-507c3213c3b3', '9f8febd1-e96c-46c4-a5f4-84a45cc499a2', 'tenant1', '3ec83352-e8fa-4c58-ab7f-12fcdc0a7f70'),
    ('cn5', '22d7b090-b4af-49d3-999f-ca7b78b3c565', '9f8febd1-e96c-46c4-a5f4-84a45cc499a2', 'tenant2', '89216ffe-439d-4c88-b07c-3a4da24e6b11'),
    ('cn7', '306afa59-3b12-4601-9f34-43f7db3acb5d', '9f8febd1-e96c-46c4-a5f4-84a45cc499a2', 'tenant1', '8bc5bf56-d9a0-4e65-83a5-95d190ffb803'),
    ('cn8', '7e5eca0f-9a92-4a8e-a792-be9b0dd8f1ee', '9f8febd1-e96c-46c4-a5f4-84a45cc499a2', 'tenant1', '59583815-e8ae-41d4-ac83-52b8f0a50f61'),
    ('cna', '921b3eb4-06f7-41f5-aabc-3c334337a121', '9f8febd1-e96c-46c4-a5f4-84a45cc499a2', 'tenant1', '9b743643-d7ab-477b-3175-249a50b3191e'),
    ('cna', 'bace7c12-f1be-4d6f-a385-4d2075db9a55', '9f8febd1-e96c-46c4-a5f4-84a45cc499a2', 'tenant1', '17ac3b08-5472-4ea7-a8f8-8820f83368ba');