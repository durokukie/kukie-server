-- GPU 화면에서 고르는 허깅페이스 모델 후보. 팀에 속하지 않는 전체 공용 목록이고, 갱신마다 통째로 맞춘다.
create table tbl_model_candidate(
    id uuid primary key,
    model_id varchar(200) not null,
    params_b double precision not null,
    size_gb double precision,
    architecture varchar(200) not null,
    license varchar(100) not null,
    license_note varchar(50),
    vllm_support varchar(20) not null,
    gated varchar(20) not null,
    fits_gpus jsonb not null,
    likes integer not null,
    registered_on date,
    display_order integer not null,
    collected_at timestamp not null,
    previous_collected_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_model_candidate_model unique (model_id)
);
