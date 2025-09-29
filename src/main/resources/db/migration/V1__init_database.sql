create table shopify_installation (
    id integer generated always as identity primary key,
    shop_domain varchar(255) not null unique,
    access_token text not null,
    scopes text not null,
    installed_at timestamptz not null,
    updated_at timestamptz not null
);

create table reai_connection (
    id integer generated always as identity primary key,
    shopify_installation_id integer references shopify_installation(id) on delete cascade,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    tenant_id bigint,
    access_token_expires_at timestamptz,
    access_token text,
    unique (shopify_installation_id)
);

create table reai_order_sync (
    id integer generated always as identity primary key,
    reai_order_id bigint,
    reai_invoice_id bigint,
    shopify_order_number varchar(128) not null,
    reai_invoice_number varchar(128),
    synced_at timestamptz not null,
    tenant_id BIGINT NOT NULL DEFAULT 0
);
