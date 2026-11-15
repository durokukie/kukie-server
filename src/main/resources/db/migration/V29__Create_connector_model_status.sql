create table tbl_connector_model_status (
    cluster_id uuid primary key references tbl_connector_credential(cluster_id) on delete cascade,
    token_hash varchar(64) not null,
    connected boolean not null,
    checked_at timestamp,
    received_at timestamp not null,
    ready_models jsonb not null
);
