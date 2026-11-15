create table tbl_serving_access (
    id uuid primary key,
    cluster_id uuid not null references tbl_cluster(id) on delete cascade,
    email varchar(255) not null,
    invited_by uuid not null references tbl_user(id),
    user_id uuid references tbl_user(id) on delete cascade,
    status varchar(10) not null check (status in ('PENDING', 'ACTIVE', 'DECLINED', 'REVOKED', 'EXPIRED')),
    invitation_expires_at timestamp not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint ck_serving_active_user check (status <> 'ACTIVE' or user_id is not null)
);

create unique index uk_serving_access_live_email on tbl_serving_access(cluster_id, email)
    where status in ('PENDING', 'ACTIVE');
create unique index uk_serving_access_active_user on tbl_serving_access(cluster_id, user_id)
    where status = 'ACTIVE';
create index idx_serving_access_user_status on tbl_serving_access(user_id, status);
create index idx_serving_access_email_status on tbl_serving_access(email, status);
