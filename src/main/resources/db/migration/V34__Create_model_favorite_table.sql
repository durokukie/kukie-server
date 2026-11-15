-- GPU 화면 후보 표의 클러스터별 즐겨찾기. 후보 목록 갱신이 다운로드 순위에서 빠진 즐겨찾기 모델도 따로 받아 남긴다.
-- 후보에서 모델을 시작하면 그 클러스터의 즐겨찾기에 저절로 들어간다.
create table tbl_model_favorite (
    id uuid primary key,
    cluster_id uuid not null references tbl_cluster(id) on delete cascade,
    model_id varchar(200) not null,
    created_by uuid not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_model_favorite_cluster_model unique (cluster_id, model_id)
);
