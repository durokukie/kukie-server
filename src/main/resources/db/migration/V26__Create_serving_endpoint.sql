create table tbl_serving_endpoint (
    id uuid primary key,
    cluster_id uuid not null unique references tbl_cluster(id) on delete cascade,
    base_url varchar(512) not null,
    encrypted_token text not null,
    models jsonb not null,
    created_at timestamp not null,
    updated_at timestamp not null
);
