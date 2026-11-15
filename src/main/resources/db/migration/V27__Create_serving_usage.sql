create table tbl_serving_usage (
    id uuid primary key,
    key_id uuid not null references tbl_serving_api_key(id) on delete cascade,
    access_id uuid not null references tbl_serving_access(id) on delete cascade,
    cluster_id uuid not null references tbl_cluster(id) on delete cascade,
    user_id uuid not null references tbl_user(id) on delete cascade,
    model varchar(200),
    started_at timestamp not null,
    deadline_at timestamp not null,
    finished_at timestamp,
    duration_millis bigint,
    http_status integer,
    wire_status integer,
    outcome varchar(16) not null check (outcome in ('IN_PROGRESS','COMPLETED','FAILED','TIMEOUT','CANCELLED','INCOMPLETE','INTERRUPTED')),
    input_tokens bigint check (input_tokens >= 0),
    output_tokens bigint check (output_tokens >= 0),
    usage_missing boolean not null,
    created_at timestamp not null,
    updated_at timestamp not null
);
create index idx_serving_usage_cluster_time on tbl_serving_usage(cluster_id, started_at);
create index idx_serving_usage_user_time on tbl_serving_usage(cluster_id, user_id, started_at);
create index idx_serving_usage_pending on tbl_serving_usage(deadline_at) where outcome = 'IN_PROGRESS';
create index idx_serving_usage_retention on tbl_serving_usage(started_at);
