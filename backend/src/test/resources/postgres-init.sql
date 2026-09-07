CREATE ROLE idax_app NOINHERIT;
CREATE ROLE idax_admin NOINHERIT;
CREATE SCHEMA idax_core;
CREATE TABLE idax_core.tenant(tenant_id UUID PRIMARY KEY);
CREATE TABLE idax_core.idax_permission(
    permission_code VARCHAR(160) PRIMARY KEY,
    module_key VARCHAR(80) NOT NULL,
    resource_key VARCHAR(120) NOT NULL,
    field_key VARCHAR(120),
    action_key VARCHAR(40) NOT NULL,
    label_key VARCHAR(200),
    api_path VARCHAR(240),
    description VARCHAR(500),
    source_type VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_idax_permission_resource_action
    ON idax_core.idax_permission(resource_key, action_key) WHERE field_key IS NULL;
CREATE UNIQUE INDEX uq_idax_permission_resource_field_action
    ON idax_core.idax_permission(resource_key, field_key, action_key) WHERE field_key IS NOT NULL;
CREATE TABLE idax_core.idax_role(
    role_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    role_key VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    system_role BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE idax_core.idax_role_permission(
    role_id UUID NOT NULL,
    permission_code VARCHAR(160) NOT NULL,
    PRIMARY KEY(role_id, permission_code)
);
CREATE TABLE idax_core.tenant_user(
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'user',
    PRIMARY KEY(tenant_id, user_id)
);
CREATE TABLE idax_core.idax_user_role(
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY(tenant_id, user_id, role_id)
);
