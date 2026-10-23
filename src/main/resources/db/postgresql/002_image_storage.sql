CREATE TABLE image_objects (
    namespace TEXT NOT NULL CHECK (namespace IN ('profiles', 'listings')),
    filename TEXT NOT NULL,
    sha256 TEXT NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    byte_size BIGINT NOT NULL CHECK (byte_size BETWEEN 1 AND 10485760),
    state TEXT NOT NULL CHECK (state IN ('PENDING', 'ACTIVE', 'RETIRED')),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL CHECK (updated_at >= created_at),
    PRIMARY KEY (namespace, filename)
);

CREATE TABLE image_storage_bindings (
    namespace TEXT PRIMARY KEY CHECK (namespace IN ('profiles', 'listings')),
    project_origin TEXT NOT NULL,
    bucket_id TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at BIGINT NOT NULL,
    UNIQUE (project_origin, bucket_id)
);

INSERT INTO image_storage_bindings(namespace, project_origin, bucket_id, active, updated_at)
VALUES ('profiles', 'https://unconfigured.invalid', 'profileImages', FALSE, 0),
       ('listings', 'https://unconfigured.invalid', 'listingImages', FALSE, 0);
