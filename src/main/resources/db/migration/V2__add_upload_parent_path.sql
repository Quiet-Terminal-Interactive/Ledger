ALTER TABLE uploads ADD COLUMN parent_path character varying(255) NOT NULL DEFAULT '';
ALTER TABLE uploads ALTER COLUMN parent_path DROP DEFAULT;
