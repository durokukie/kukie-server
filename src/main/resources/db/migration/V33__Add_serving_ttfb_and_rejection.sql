alter table tbl_serving_usage add column ttfb_millis bigint check (ttfb_millis >= 0);

create table tbl_serving_rejection (
    id uuid primary key,
    key_id uuid not null references tbl_serving_api_key(id) on delete cascade,
    access_id uuid not null references tbl_serving_access(id) on delete cascade,
    cluster_id uuid not null references tbl_cluster(id) on delete cascade,
    user_id uuid not null references tbl_user(id) on delete cascade,
    model varchar(200),
    reason varchar(32) not null check (reason in ('concurrency_limit','connector_busy','model_not_allowed','invalid_request','request_too_large','upstream_unavailable')),
    minute timestamp not null,
    count integer not null check (count > 0),
    created_at timestamp not null
);
create index idx_serving_rejection_cluster_minute on tbl_serving_rejection(cluster_id, minute);
create index idx_serving_rejection_retention on tbl_serving_rejection(minute);
