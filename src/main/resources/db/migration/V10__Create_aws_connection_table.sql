create table tbl_aws_connection(
    id uuid primary key,
    team_id uuid not null references tbl_team(id),
    external_id varchar(64) not null,
    created_by uuid not null references tbl_user(id),
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_aws_connection_team unique (team_id),
    constraint uk_aws_connection_external_id unique (external_id)
);
