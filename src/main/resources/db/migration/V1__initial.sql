CREATE TABLE floors (
  id BIGINT PRIMARY KEY,
  label TEXT NOT NULL,
  width INTEGER NOT NULL CHECK (width > 0),
  height INTEGER NOT NULL CHECK (height > 0)
);

CREATE TABLE rooms (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  floor_id BIGINT NOT NULL REFERENCES floors(id),
  number VARCHAR(32) NOT NULL,
  name VARCHAR(160) NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  x INTEGER NOT NULL CHECK (x >= 0),
  y INTEGER NOT NULL CHECK (y >= 0),
  width INTEGER NOT NULL CHECK (width > 0),
  height INTEGER NOT NULL CHECK (height > 0),
  status VARCHAR(16) NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published')),
  version INTEGER NOT NULL DEFAULT 1,
  CONSTRAINT rooms_floor_number UNIQUE (floor_id, number)
);

CREATE INDEX rooms_public_floor_idx ON rooms(floor_id) WHERE status = 'published';

INSERT INTO floors (id, label, width, height) VALUES
  (1, '1 этаж', 1000, 1000),
  (2, '2 этаж', 1000, 1000),
  (3, '3 этаж', 1000, 1000),
  (4, '4 этаж', 1000, 1000);
