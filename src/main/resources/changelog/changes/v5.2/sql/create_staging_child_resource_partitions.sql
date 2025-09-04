-- Create partitions for staging_subject table based on first character of id
-- Numeric partitions (0-9)
CREATE TABLE staging_subject_0 PARTITION OF staging_subject FOR VALUES FROM ('0') TO ('1');
CREATE TABLE staging_subject_1 PARTITION OF staging_subject FOR VALUES FROM ('1') TO ('2');
CREATE TABLE staging_subject_2 PARTITION OF staging_subject FOR VALUES FROM ('2') TO ('3');
CREATE TABLE staging_subject_3 PARTITION OF staging_subject FOR VALUES FROM ('3') TO ('4');
CREATE TABLE staging_subject_4 PARTITION OF staging_subject FOR VALUES FROM ('4') TO ('5');
CREATE TABLE staging_subject_5 PARTITION OF staging_subject FOR VALUES FROM ('5') TO ('6');
CREATE TABLE staging_subject_6 PARTITION OF staging_subject FOR VALUES FROM ('6') TO ('7');
CREATE TABLE staging_subject_7 PARTITION OF staging_subject FOR VALUES FROM ('7') TO ('8');
CREATE TABLE staging_subject_8 PARTITION OF staging_subject FOR VALUES FROM ('8') TO ('9');
CREATE TABLE staging_subject_9 PARTITION OF staging_subject FOR VALUES FROM ('9') TO ('a');

-- Alphabetic partitions (a-f for hash-based IDs)
CREATE TABLE staging_subject_a PARTITION OF staging_subject FOR VALUES FROM ('a') TO ('b');
CREATE TABLE staging_subject_b PARTITION OF staging_subject FOR VALUES FROM ('b') TO ('c');
CREATE TABLE staging_subject_c PARTITION OF staging_subject FOR VALUES FROM ('c') TO ('d');
CREATE TABLE staging_subject_d PARTITION OF staging_subject FOR VALUES FROM ('d') TO ('e');
CREATE TABLE staging_subject_e PARTITION OF staging_subject FOR VALUES FROM ('e') TO ('f');
CREATE TABLE staging_subject_f PARTITION OF staging_subject FOR VALUES FROM ('f') TO ('g');

-- Create partitions for staging_contributor table based on first character of id
-- Numeric partitions (0-9)
CREATE TABLE staging_contributor_0 PARTITION OF staging_contributor FOR VALUES FROM ('0') TO ('1');
CREATE TABLE staging_contributor_1 PARTITION OF staging_contributor FOR VALUES FROM ('1') TO ('2');
CREATE TABLE staging_contributor_2 PARTITION OF staging_contributor FOR VALUES FROM ('2') TO ('3');
CREATE TABLE staging_contributor_3 PARTITION OF staging_contributor FOR VALUES FROM ('3') TO ('4');
CREATE TABLE staging_contributor_4 PARTITION OF staging_contributor FOR VALUES FROM ('4') TO ('5');
CREATE TABLE staging_contributor_5 PARTITION OF staging_contributor FOR VALUES FROM ('5') TO ('6');
CREATE TABLE staging_contributor_6 PARTITION OF staging_contributor FOR VALUES FROM ('6') TO ('7');
CREATE TABLE staging_contributor_7 PARTITION OF staging_contributor FOR VALUES FROM ('7') TO ('8');
CREATE TABLE staging_contributor_8 PARTITION OF staging_contributor FOR VALUES FROM ('8') TO ('9');
CREATE TABLE staging_contributor_9 PARTITION OF staging_contributor FOR VALUES FROM ('9') TO ('a');

-- Alphabetic partitions (a-f for hash-based IDs)
CREATE TABLE staging_contributor_a PARTITION OF staging_contributor FOR VALUES FROM ('a') TO ('b');
CREATE TABLE staging_contributor_b PARTITION OF staging_contributor FOR VALUES FROM ('b') TO ('c');
CREATE TABLE staging_contributor_c PARTITION OF staging_contributor FOR VALUES FROM ('c') TO ('d');
CREATE TABLE staging_contributor_d PARTITION OF staging_contributor FOR VALUES FROM ('d') TO ('e');
CREATE TABLE staging_contributor_e PARTITION OF staging_contributor FOR VALUES FROM ('e') TO ('f');
CREATE TABLE staging_contributor_f PARTITION OF staging_contributor FOR VALUES FROM ('f') TO ('g');

-- Create partitions for staging_classification table based on first character of id
-- Numeric partitions (0-9)
CREATE TABLE staging_classification_0 PARTITION OF staging_classification FOR VALUES FROM ('0') TO ('1');
CREATE TABLE staging_classification_1 PARTITION OF staging_classification FOR VALUES FROM ('1') TO ('2');
CREATE TABLE staging_classification_2 PARTITION OF staging_classification FOR VALUES FROM ('2') TO ('3');
CREATE TABLE staging_classification_3 PARTITION OF staging_classification FOR VALUES FROM ('3') TO ('4');
CREATE TABLE staging_classification_4 PARTITION OF staging_classification FOR VALUES FROM ('4') TO ('5');
CREATE TABLE staging_classification_5 PARTITION OF staging_classification FOR VALUES FROM ('5') TO ('6');
CREATE TABLE staging_classification_6 PARTITION OF staging_classification FOR VALUES FROM ('6') TO ('7');
CREATE TABLE staging_classification_7 PARTITION OF staging_classification FOR VALUES FROM ('7') TO ('8');
CREATE TABLE staging_classification_8 PARTITION OF staging_classification FOR VALUES FROM ('8') TO ('9');
CREATE TABLE staging_classification_9 PARTITION OF staging_classification FOR VALUES FROM ('9') TO ('a');

-- Alphabetic partitions (a-f for hash-based IDs)
CREATE TABLE staging_classification_a PARTITION OF staging_classification FOR VALUES FROM ('a') TO ('b');
CREATE TABLE staging_classification_b PARTITION OF staging_classification FOR VALUES FROM ('b') TO ('c');
CREATE TABLE staging_classification_c PARTITION OF staging_classification FOR VALUES FROM ('c') TO ('d');
CREATE TABLE staging_classification_d PARTITION OF staging_classification FOR VALUES FROM ('d') TO ('e');
CREATE TABLE staging_classification_e PARTITION OF staging_classification FOR VALUES FROM ('e') TO ('f');
CREATE TABLE staging_classification_f PARTITION OF staging_classification FOR VALUES FROM ('f') TO ('g');

-- Create partitions for staging_call_number table based on first character of id
-- Numeric partitions (0-9)
CREATE TABLE staging_call_number_0 PARTITION OF staging_call_number FOR VALUES FROM ('0') TO ('1');
CREATE TABLE staging_call_number_1 PARTITION OF staging_call_number FOR VALUES FROM ('1') TO ('2');
CREATE TABLE staging_call_number_2 PARTITION OF staging_call_number FOR VALUES FROM ('2') TO ('3');
CREATE TABLE staging_call_number_3 PARTITION OF staging_call_number FOR VALUES FROM ('3') TO ('4');
CREATE TABLE staging_call_number_4 PARTITION OF staging_call_number FOR VALUES FROM ('4') TO ('5');
CREATE TABLE staging_call_number_5 PARTITION OF staging_call_number FOR VALUES FROM ('5') TO ('6');
CREATE TABLE staging_call_number_6 PARTITION OF staging_call_number FOR VALUES FROM ('6') TO ('7');
CREATE TABLE staging_call_number_7 PARTITION OF staging_call_number FOR VALUES FROM ('7') TO ('8');
CREATE TABLE staging_call_number_8 PARTITION OF staging_call_number FOR VALUES FROM ('8') TO ('9');
CREATE TABLE staging_call_number_9 PARTITION OF staging_call_number FOR VALUES FROM ('9') TO ('a');

-- Alphabetic partitions (a-f for hash-based IDs)
CREATE TABLE staging_call_number_a PARTITION OF staging_call_number FOR VALUES FROM ('a') TO ('b');
CREATE TABLE staging_call_number_b PARTITION OF staging_call_number FOR VALUES FROM ('b') TO ('c');
CREATE TABLE staging_call_number_c PARTITION OF staging_call_number FOR VALUES FROM ('c') TO ('d');
CREATE TABLE staging_call_number_d PARTITION OF staging_call_number FOR VALUES FROM ('d') TO ('e');
CREATE TABLE staging_call_number_e PARTITION OF staging_call_number FOR VALUES FROM ('e') TO ('f');
CREATE TABLE staging_call_number_f PARTITION OF staging_call_number FOR VALUES FROM ('f') TO ('g');