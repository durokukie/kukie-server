create table tbl_report(
    id uuid primary key,
    kind varchar(20) not null,
    cluster_id uuid not null references tbl_cluster(id),
    conversation_id uuid,
    owner_id uuid references tbl_user(id),
    shared boolean not null,
    title varchar(200) not null,
    content text,
    status varchar(20) not null,
    period_start timestamp,
    period_end timestamp,
    through_turn integer,
    ai_refined boolean not null,
    time_zone varchar(50) not null,
    version integer not null,
    edited_by uuid,
    edited_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_report_cluster on tbl_report(cluster_id);
create index idx_report_generating on tbl_report(updated_at) where status = 'GENERATING';

-- 자동으로 만드는 주간 보고서는 클러스터의 한 회차에 하나만. 사람이 직접 만든 것은 막지 않는다.
create unique index uk_report_scheduled on tbl_report(cluster_id, period_end) where kind = 'WEEKLY' and owner_id is null;

alter table tbl_team add column report_day varchar(10) not null default 'MONDAY';
alter table tbl_team add column report_time time not null default '09:00';
alter table tbl_team add column report_zone varchar(50) not null default 'Asia/Seoul';

-- 다음 월요일 09:00(Asia/Seoul). 배포하는 동안 이전 버전 서버가 만드는 팀도 이 기본값을 받는다
alter table tbl_team add column report_next_at timestamp not null default (
    (
        date_trunc('week', now() at time zone 'Asia/Seoul') + interval '9 hours'
        + case
            when date_trunc('week', now() at time zone 'Asia/Seoul') + interval '9 hours' > now() at time zone 'Asia/Seoul' then interval '0'
            else interval '7 days'
        end
    ) at time zone 'Asia/Seoul' at time zone 'UTC'
);

create index idx_team_report_next_at on tbl_team(report_next_at);

alter table tbl_notification add column report_id uuid;
alter table tbl_notification add column subject varchar(200);
