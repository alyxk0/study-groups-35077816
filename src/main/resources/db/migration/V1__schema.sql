CREATE SEQUENCE object_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE coordinates (
    id BIGINT PRIMARY KEY DEFAULT nextval('object_seq') CHECK (id > 0),
    version BIGINT NOT NULL DEFAULT 0,
    x BIGINT NOT NULL,
    y DOUBLE PRECISION NOT NULL CHECK (y NOT IN ('Infinity'::float8, '-Infinity'::float8, 'NaN'::float8))
);
CREATE TABLE location (
    id BIGINT PRIMARY KEY DEFAULT nextval('object_seq') CHECK (id > 0),
    version BIGINT NOT NULL DEFAULT 0,
    x DOUBLE PRECISION NOT NULL CHECK (x NOT IN ('Infinity'::float8, '-Infinity'::float8, 'NaN'::float8)),
    y REAL NOT NULL CHECK (y NOT IN ('Infinity'::float4, '-Infinity'::float4, 'NaN'::float4)),
    z BIGINT NOT NULL
);
CREATE TABLE person (
    id BIGINT PRIMARY KEY DEFAULT nextval('object_seq') CHECK (id > 0),
    version BIGINT NOT NULL DEFAULT 0,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    eye_color VARCHAR(20) CHECK (eye_color IN ('GREEN', 'ORANGE', 'BROWN')),
    hair_color VARCHAR(20) CHECK (hair_color IN ('GREEN', 'ORANGE', 'BROWN')),
    location_id BIGINT NOT NULL REFERENCES location(id) ON DELETE RESTRICT,
    birthday TIMESTAMP WITH TIME ZONE NOT NULL,
    nationality VARCHAR(30) NOT NULL CHECK (nationality IN ('UNITED_KINGDOM', 'GERMANY', 'VATICAN', 'SOUTH_KOREA'))
);
CREATE TABLE study_group (
    id BIGINT PRIMARY KEY DEFAULT nextval('object_seq') CHECK (id > 0),
    version BIGINT NOT NULL DEFAULT 0,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    coordinates_id BIGINT NOT NULL REFERENCES coordinates(id) ON DELETE RESTRICT,
    creation_date TIMESTAMP NOT NULL DEFAULT localtimestamp,
    students_count BIGINT NOT NULL CHECK (students_count > 0),
    expelled_students INTEGER NOT NULL CHECK (expelled_students > 0),
    transferred_students INTEGER NOT NULL CHECK (transferred_students > 0),
    form_of_education VARCHAR(30) NOT NULL CHECK (form_of_education IN ('DISTANCE_EDUCATION', 'FULL_TIME_EDUCATION', 'EVENING_CLASSES')),
    should_be_expelled INTEGER NOT NULL CHECK (should_be_expelled > 0),
    average_mark DOUBLE PRECISION NOT NULL CHECK (average_mark > 0 AND average_mark NOT IN ('Infinity'::float8, '-Infinity'::float8, 'NaN'::float8)),
    semester_enum VARCHAR(10) NOT NULL CHECK (semester_enum IN ('FIRST', 'SECOND', 'THIRD', 'FOURTH', 'EIGHTH')),
    group_admin_id BIGINT REFERENCES person(id) ON DELETE RESTRICT
);
CREATE INDEX group_name_idx ON study_group(name);
CREATE INDEX group_coordinates_idx ON study_group(coordinates_id);
CREATE INDEX group_admin_idx ON study_group(group_admin_id);
CREATE INDEX person_location_idx ON person(location_id);
CREATE TABLE app_user (
    id BIGINT PRIMARY KEY DEFAULT nextval('object_seq') CHECK (id > 0),
    version BIGINT NOT NULL DEFAULT 0,
    username VARCHAR(40) NOT NULL UNIQUE CHECK (username ~ '^[a-zA-Z0-9_]{3,40}$'),
    password_hash VARCHAR(100) NOT NULL
);
