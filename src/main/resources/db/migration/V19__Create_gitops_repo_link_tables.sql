-- GitHub App 설치 한 개는 한 팀에만 묶인다. 팀·클러스터를 지우면 설치·레포 연결도 함께 지운다.
create table tbl_github_installation(
    id uuid primary key,
    team_id uuid not null references tbl_team(id) on delete cascade,
    installation_id bigint not null,
    account_login varchar(100) not null,
    account_type varchar(20) not null,
    repository_selection varchar(20) not null,
    repositories jsonb not null,
    suspended boolean not null,
    installed_by uuid not null references tbl_user(id),
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_github_installation_installation unique (installation_id)
);

create index idx_github_installation_team on tbl_github_installation(team_id);

-- 조직 관리자 승인을 기다리는 설치 요청. 팀마다 하나.
create table tbl_github_install_request(
    team_id uuid primary key references tbl_team(id) on delete cascade,
    requested_by uuid not null references tbl_user(id),
    requested_at timestamp not null
);

create table tbl_manifest_repo(
    id uuid primary key,
    team_id uuid not null references tbl_team(id) on delete cascade,
    cluster_id uuid not null references tbl_cluster(id) on delete cascade,
    installation_id bigint not null,
    repository_id bigint not null,
    full_name varchar(200) not null,
    connected_by uuid not null references tbl_user(id),
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_manifest_repo_cluster_repository unique (cluster_id, repository_id)
);

alter table tbl_chat_session add column repo_link_state jsonb;
