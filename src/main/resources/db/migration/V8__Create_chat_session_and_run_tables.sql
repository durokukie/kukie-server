create table tbl_chat_session(
    id uuid primary key,
    user_id uuid not null references tbl_user(id),
    cluster_id uuid not null references tbl_cluster(id),
    title varchar(200) not null,
    current_mode varchar(20) not null,
    context_name varchar(200) not null,
    namespace varchar(63) not null,
    cluster_fingerprint varchar(64) not null,
    visibility varchar(20) not null,
    version integer not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_chat_session_user on tbl_chat_session(user_id);
create index idx_chat_session_cluster on tbl_chat_session(cluster_id);

create table tbl_chat_run(
    id uuid primary key,
    session_id uuid not null references tbl_chat_session(id),
    request_id varchar(64) not null,
    turn_no integer not null,
    kind varchar(20) not null,
    mode varchar(20) not null,
    status varchar(20) not null,
    input_text text,
    response_payload jsonb,
    agent_messages jsonb,
    usage_summary jsonb,
    started_at timestamp not null,
    finished_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_chat_run_request unique (session_id, request_id),
    constraint uk_chat_run_turn unique (session_id, turn_no)
);

create unique index uk_chat_run_active on tbl_chat_run(session_id)
    where status in ('running', 'awaiting_approval', 'recovery_required');
