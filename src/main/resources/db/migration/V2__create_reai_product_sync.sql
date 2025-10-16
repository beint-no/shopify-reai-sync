create table reai_product_sync (
    id integer generated always as identity primary key,
    shop_domain varchar(255) not null,
    shopify_product_gid varchar(255) not null,
    tenant_id bigint not null,
    reai_product_id bigint,
    product_title text,
    synced_at timestamptz not null default now(),
    constraint uq_product_sync unique (shopify_product_gid, tenant_id)
);

create index idx_reai_product_sync_shop_domain on reai_product_sync(shop_domain);
