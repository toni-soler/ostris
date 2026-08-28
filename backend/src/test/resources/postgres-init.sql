CREATE ROLE idax_app NOINHERIT;
CREATE ROLE idax_admin NOINHERIT;
CREATE SCHEMA idax_core;
CREATE TABLE idax_core.tenant(tenant_id UUID PRIMARY KEY);
CREATE TABLE idax_core.idax_permission(
    permission_code VARCHAR(160) PRIMARY KEY,
    module_key VARCHAR(80) NOT NULL,
    resource_key VARCHAR(120) NOT NULL,
    action_key VARCHAR(80) NOT NULL,
    label_key VARCHAR(200),
    api_path VARCHAR(500),
    description VARCHAR(1000),
    source_type VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE idax_core.idax_role(
    role_id UUID PRIMARY KEY,
    role_key VARCHAR(80) NOT NULL,
    system_role BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE idax_core.idax_role_permission(
    role_id UUID NOT NULL,
    permission_code VARCHAR(160) NOT NULL,
    PRIMARY KEY(role_id, permission_code)
);
