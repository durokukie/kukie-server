-- 코딩 에이전트가 컴퓨터에 두고 쓰는 호스팅 배포 키. 키 자체는 두지 않고 SHA-256 해시만 둔다.
create table tbl_hosting_agent_key (
    id uuid primary key,
    user_id uuid not null,
    key_hash varchar(64) not null,
    last_used_at timestamp not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_hosting_agent_key_hash unique (key_hash)
);

create index idx_hosting_agent_key_user on tbl_hosting_agent_key (user_id);
