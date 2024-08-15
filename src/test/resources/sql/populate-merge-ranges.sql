INSERT INTO merge_range (id, entity_type, tenant_id, lower, upper, created_at, finished_at)
VALUES
    ('b7df83a1-8b15-46c1-9a4c-9d2dbb3cf4d6', 'holding', 'consortium', 'cafc6429-8e7c-488c-be9d-ecc45e7659ec', '84aac856-c4ab-426c-8302-9ab0e6a295c0', current_timestamp, current_timestamp),
    ('dfb20d52-7f1f-4b5b-a492-2e47d2c0ac59', 'holding', 'member_tenant', '9c2c968d-c642-4403-a449-2ec40afcd736', '8751df7c-a588-4d86-8268-8c153bb98b16', current_timestamp, current_timestamp),
    ('2f23b9fa-9e1a-44ff-a30f-61ec5f3adcc8', 'item', 'member_tenant', '9c2c968d-c642-4403-a449-2ec40afcd736', '8751df7c-a588-4d86-8268-8c153bb98b16', current_timestamp, current_timestamp),
    ('9f8febd1-e96c-46c4-a5f4-84a45cc499a2', 'instance', 'consortium', '9c2c968d-c642-4403-a449-2ec40afcd736', '8751df7c-a588-4d86-8268-8c153bb98b16', current_timestamp, current_timestamp);
