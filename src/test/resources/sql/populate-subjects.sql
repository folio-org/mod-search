INSERT INTO subject (id, value, authority_id)
VALUES
    ('1', 'Genre', 'b7df83a1-8b15-46c1-9a4c-9d2dbb3cf4d6'),
    ('2', 'Sci-Fi', 'dfb20d52-7f1f-4b5b-a492-2e47d2c0ac59'),
    ('3', 'Drama', '652b293e-e742-456e-b9e9-6e98655d89e6'),
    ('4', 'Comedy', '2f23b9fa-9e1a-44ff-a30f-61ec5f3adcc8'),
    ('5', 'Thriller', 'c00a8290-8e6f-4bb9-8530-c8575c8d54a8'),
    ('6', 'Romance', 'cafc6429-8e7c-488c-be9d-ecc45e7659ec'),
    ('7', 'Horror', '72e2d771-2b05-41f2-afae-9e0b140b767d'),
    ('8', 'Action', NULL),
    ('9', 'Adventure', '0e80645b-0293-4daa-8a0a-2c1a43b26b5d'),
    ('10', 'Mystery', '1310782d-bdce-4038-97a1-f4036542f97c'),
    ('11', 'Western', 'b0aa3a38-cd9c-46bd-9d88-d95eaa379c41'),
    ('12', 'Animation', '84aac856-c4ab-426c-8302-9ab0e6a295c0'),
    ('13', 'Musical', NULL),
    ('14', 'Film-Noir', '9c2c968d-c642-4403-a449-2ec40afcd736'),
    ('15', 'Biography', '9f8febd1-e96c-46c4-a5f4-84a45cc499a2'),
    ('16', 'Children', '8751df7c-a588-4d86-8268-8c153bb98b16'),
    ('17', 'Crime', NULL),
    ('18', 'Drama/Romance', NULL),
    ('19', 'Fantasy', NULL),
    ('20', 'Alternative History', NULL),
    ('21', 'History', '79144653-7a98-4dfb-aa6a-13ad49e80952');


INSERT INTO instance_subject (subject_id, instance_id, tenant_id, shared)
VALUES
    ('1', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'::uuid, 'member_tenant', false),
    ('2', 'b3bae8a9-cfb1-4afe-83d5-2cdae4580e07'::uuid, 'consortium', true),
    ('2', '9ec55e4f-6a76-427c-b47b-197046f44a54'::uuid, 'member_tenant', false),
    ('2', 'aab8fff4-49c6-4578-979e-439b6ba3600a'::uuid, 'consortium', true),
    ('3', '87c7e447-3f7d-40e1-8f30-833d46f4e4bb'::uuid, 'member_tenant', false),
    ('4', '94944cb2-873a-42fa-bf90-2198ea1b9c1b'::uuid, 'consortium', true),
    ('5', 'b1df7614-1a81-4911-95b3-76b2796668d0'::uuid, 'member_tenant', false),
    ('6', 'a9d83347-8c0e-4e04-92ee-0edf5e8f98b3'::uuid, 'consortium', true),
    ('7', 'd7cf934a-e0a3-43b5-9061-0b4a938b9883'::uuid, 'member_tenant', false),
    ('8', 'd7f64d94-59d4-4273-899e-af82d179bf8b'::uuid, 'consortium', true),
    ('9', '3ddd69b4-ed7d-482a-b4bd-63e9529a1d8c'::uuid, 'member_tenant', false),
    ('10', '5076586c-0697-44dc-9b00-b0f566dbf67d'::uuid, 'consortium', true),
    ('11', '6b8b1d00-0ff7-4440-9441-741c945b9fad'::uuid, 'member_tenant', false),
    ('12', 'a5d16c0b-3089-49c3-a938-7fb156506102'::uuid, 'consortium', true),
    ('13', 'd557918a-394a-4b52-abd1-ff472c591415'::uuid, 'member_tenant', false),
    ('14', 'fab25318-969f-4690-ad6a-a00361f2b77a'::uuid, 'consortium', true),
    ('15', '05bc7c8d-ac2c-4c72-a6fd-32f2234a2260'::uuid, 'member_tenant', false),
    ('16', '831057d4-3714-4e48-aa34-7ac0547453e2'::uuid, 'consortium', true),
    ('17', '9315038b-8e29-484d-bb21-713a6847bbcf'::uuid, 'member_tenant', false),
    ('17', '9ec55e4f-6a76-427c-b47b-197046f44a54'::uuid, 'member_tenant', false),
    ('18', '3ced7086-fd8c-49da-9782-87a5f9ef2a59'::uuid, 'consortium', true),
    ('19', '9ec55e4f-6a76-427c-b47b-197046f44a54'::uuid, 'member_tenant', false),
    ('20', 'aab8fff4-49c6-4578-979e-439b6ba3600a'::uuid, 'consortium', true),
    ('21', 'aab8fff4-49c6-4578-979e-439b6ba3600a'::uuid, 'consortium', true);