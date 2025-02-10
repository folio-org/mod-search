INSERT INTO merge_range (id, entity_type, tenant_id, lower, upper, created_at, finished_at, status, fail_cause)
VALUES
    ('b7df83a1-8b15-46c1-9a4c-9d2dbb3cf4d6', 'holdings', 'consortium', 'cafc64298e7c488cbe9decc45e7659ec', '84aac856c4ab426c83029ab0e6a295c0', current_timestamp, current_timestamp, 'FAIL', 'Some error'),
    ('dfb20d52-7f1f-4b5b-a492-2e47d2c0ac59', 'holdings', 'member_tenant', '9c2c968dc6424403a4492ec40afcd736', '8751df7ca5884d8682688c153bb98b16', current_timestamp, current_timestamp, 'FAIL', 'Some error'),
    ('2f23b9fa-9e1a-44ff-a30f-61ec5f3adcc8', 'item', 'member_tenant', '9c2c968dc6424403a4492ec40afcd736', '8751df7ca5884d8682688c153bb98b16', current_timestamp, current_timestamp, 'SUCCESS', null),
    ('9f8febd1-e96c-46c4-a5f4-84a45cc499a2', 'instance', 'consortium', '9c2c968dc6424403a4492ec40afcd736', '8751df7ca5884d8682688c153bb98b16', current_timestamp, null, 'FAIL', 'Some error');
