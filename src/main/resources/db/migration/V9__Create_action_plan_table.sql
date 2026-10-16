create table tbl_action_plan(
    id uuid primary key,
    code varchar(80) not null,
    run_id uuid not null references tbl_chat_run(id),
    tool_call_id varchar(64) not null,
    tool_name varchar(50) not null,
    status varchar(30) not null,
    risk varchar(20) not null,
    payload jsonb not null,
    request_hash varchar(64) not null,
    decision jsonb,
    execution_result jsonb,
    failure_reason varchar(40),
    applied_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_action_plan_code unique (code),
    constraint uk_action_plan_tool_call unique (run_id, tool_call_id),
    constraint ck_action_plan_decision_present
        check (status not in ('APPROVED', 'EXECUTING', 'APPLIED', 'EFFECT_VERIFIED') or decision is not null),
    constraint ck_action_plan_applied_has_result
        check (status not in ('APPLIED', 'EFFECT_VERIFIED') or (execution_result is not null and applied_at is not null)),
    constraint ck_action_plan_failed_has_reason
        check (status <> 'FAILED' or failure_reason is not null)
);
