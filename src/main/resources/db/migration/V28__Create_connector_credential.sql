create table tbl_connector_credential (
    cluster_id uuid primary key references tbl_cluster(id) on delete cascade,
    token_hash varchar(64) not null,
    models jsonb not null,
    expires_at timestamp not null,
    created_at timestamp not null,
    updated_at timestamp not null
);
