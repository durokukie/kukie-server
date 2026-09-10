create table tbl_team(
    id uuid primary key,
    name varchar(50) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table tbl_team_membership(
    id uuid primary key,
    team_id uuid not null references tbl_team(id),
    user_id uuid not null references tbl_user(id),
    role varchar(10) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_team_membership_team_user unique (team_id, user_id)
);

create index idx_team_membership_user on tbl_team_membership(user_id);
