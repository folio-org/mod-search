INSERT INTO contributor (id, name, name_type_id, authority_id)
VALUES
    ('1', 'Genre', 'b7df83a1-8b15-46c1-9a4c-9d2dbb3cf4d5', 'b7df83a1-8b15-46c1-9a4c-9d2dbb3cf4d6'),
    ('2', 'Sci-Fi', 'dfb20d52-7f1f-4b5b-a492-2e47d2c0ac58', 'dfb20d52-7f1f-4b5b-a492-2e47d2c0ac59');


INSERT INTO instance_contributor (instance_id, contributor_id, type_id, tenant_id, shared)
VALUES
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'::uuid, '1', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12', 'member_tenant', false),
    ('b3bae8a9-cfb1-4afe-83d5-2cdae4580e07'::uuid, '2', 'b3bae8a9-cfb1-4afe-83d5-2cdae4580e06', 'consortium', true),
    ('9ec55e4f-6a76-427c-b47b-197046f44a54'::uuid, '2', '9ec55e4f-6a76-427c-b47b-197046f44a53', 'member_tenant', false),
    ('aab8fff4-49c6-4578-979e-439b6ba3600a'::uuid, '2', 'aab8fff4-49c6-4578-979e-439b6ba3600b', 'consortium', true);