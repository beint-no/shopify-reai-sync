create table shopify_installation (
    id integer generated always as identity primary key,
    shop_domain varchar(255) not null unique,
    access_token text not null,
    scopes text not null,
    installed_at timestamptz not null,
    updated_at timestamptz not null
);
