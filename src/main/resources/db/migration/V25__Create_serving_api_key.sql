create table tbl_serving_api_key (
    id uuid primary key,
    access_id uuid not null references tbl_serving_access(id) on delete cascade,
    name varchar(80) not null,
    token_hash varchar(64) not null unique,
    prefix varchar(12) not null,
    expires_at timestamp not null,
    revoked_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);
create index idx_serving_api_key_access on tbl_serving_api_key(access_id, created_at desc, id desc);
