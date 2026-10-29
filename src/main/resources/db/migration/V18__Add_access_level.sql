-- 템플릿 v5 의 AccessLevel. v1~v4 스택은 알려 주지 않으므로 지금 동작(limited)으로 채운다.
alter table tbl_aws_connection add column access_level varchar(20) not null default 'limited';
-- EKS 에 마지막으로 붙이는 데 성공한 레벨. kubeconfig 클러스터는 null.
alter table tbl_cluster add column access_level varchar(20);
alter table tbl_cluster add column access_level_failed_at timestamp;
update tbl_cluster set access_level = 'limited' where provider = 'EKS';
