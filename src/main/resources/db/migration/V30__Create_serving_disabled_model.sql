create table tbl_serving_disabled_model (
    cluster_id uuid not null references tbl_cluster(id) on delete cascade,
    model varchar(200) not null,
    updated_at timestamp not null,
    primary key (cluster_id, model)
);
