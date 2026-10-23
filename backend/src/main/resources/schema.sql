CREATE TABLE IF NOT EXISTS workspace_record (
 id UUID PRIMARY KEY,
 kind VARCHAR(20) NOT NULL,
 payload CLOB NOT NULL
);
CREATE INDEX IF NOT EXISTS record_kind ON workspace_record(kind);
CREATE TABLE IF NOT EXISTS workspace_settings (
 id INTEGER PRIMARY KEY CHECK (id = 1),
 name VARCHAR(80) NOT NULL,
 intention VARCHAR(240) NOT NULL
);

CREATE TABLE IF NOT EXISTS workspace_guard (id INTEGER PRIMARY KEY);
MERGE INTO workspace_guard(id) KEY(id) VALUES(1);
