CREATE SCHEMA s466287;
CREATE TABLE s466287.coordinates (legacy_value TEXT NOT NULL);
INSERT INTO s466287.coordinates VALUES ('existing data');
CREATE TABLE s466287.person (legacy_value TEXT NOT NULL);
INSERT INTO s466287.person VALUES ('existing person');
CREATE TABLE s466287.location (legacy_value TEXT NOT NULL);
INSERT INTO s466287.location VALUES ('existing location');
CREATE SEQUENCE s466287.object_seq START WITH 9000;
