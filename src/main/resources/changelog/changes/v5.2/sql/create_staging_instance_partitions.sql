-- Create partitions for staging_instance table based on first character of UUID
-- Numeric partitions (0-9)
CREATE TABLE staging_instance_0 PARTITION OF staging_instance FOR VALUES FROM ('0') TO ('1');
CREATE TABLE staging_instance_1 PARTITION OF staging_instance FOR VALUES FROM ('1') TO ('2');
CREATE TABLE staging_instance_2 PARTITION OF staging_instance FOR VALUES FROM ('2') TO ('3');
CREATE TABLE staging_instance_3 PARTITION OF staging_instance FOR VALUES FROM ('3') TO ('4');
CREATE TABLE staging_instance_4 PARTITION OF staging_instance FOR VALUES FROM ('4') TO ('5');
CREATE TABLE staging_instance_5 PARTITION OF staging_instance FOR VALUES FROM ('5') TO ('6');
CREATE TABLE staging_instance_6 PARTITION OF staging_instance FOR VALUES FROM ('6') TO ('7');
CREATE TABLE staging_instance_7 PARTITION OF staging_instance FOR VALUES FROM ('7') TO ('8');
CREATE TABLE staging_instance_8 PARTITION OF staging_instance FOR VALUES FROM ('8') TO ('9');
CREATE TABLE staging_instance_9 PARTITION OF staging_instance FOR VALUES FROM ('9') TO ('a');

-- Alphabetic partitions (a-f for UUIDs)
CREATE TABLE staging_instance_a PARTITION OF staging_instance FOR VALUES FROM ('a') TO ('b');
CREATE TABLE staging_instance_b PARTITION OF staging_instance FOR VALUES FROM ('b') TO ('c');
CREATE TABLE staging_instance_c PARTITION OF staging_instance FOR VALUES FROM ('c') TO ('d');
CREATE TABLE staging_instance_d PARTITION OF staging_instance FOR VALUES FROM ('d') TO ('e');
CREATE TABLE staging_instance_e PARTITION OF staging_instance FOR VALUES FROM ('e') TO ('f');
CREATE TABLE staging_instance_f PARTITION OF staging_instance FOR VALUES FROM ('f') TO ('g');