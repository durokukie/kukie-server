create table tbl_team_invitation(
    id uuid primary key,
    team_id uuid not null references tbl_team(id),
    email varchar(255) not null,
    invited_by uuid not null references tbl_user(id),
    status varchar(10) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

-- 같은 팀에 같은 주소로 대기 중인 초대는 하나만. 거절·수락된 초대는 남으므로 부분 인덱스를 쓴다.
create unique index uk_team_invitation_pending on tbl_team_invitation(team_id, email) where status = 'PENDING';

-- Inbox 는 "내 주소로 온 초대"를 찾는다.
create index idx_team_invitation_email on tbl_team_invitation(email);

create table tbl_notification(
    id uuid primary key,
    user_id uuid not null references tbl_user(id),
    kind varchar(30) not null,
    team_id uuid,
    team_name varchar(50),
    role varchar(10),
    read_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_notification_user on tbl_notification(user_id);
