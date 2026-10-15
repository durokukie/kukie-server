create table tbl_cluster(
    id uuid primary key,
    team_id uuid not null references tbl_team(id),
    registered_by uuid not null references tbl_user(id),
    name varchar(100) not null,
    provider varchar(20) not null,
    api_server varchar(300) not null,
    ca_data text,
    insecure boolean not null,
    credential text not null,
    context_name varchar(200) not null,
    default_namespace varchar(63) not null,
    fingerprint varchar(64) not null,
    status varchar(20) not null,
    last_checked_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_cluster_team_name unique (team_id, name),
    constraint ck_cluster_api_server_https check (api_server like 'https://%')
);

