-- 템플릿 v6 스택 알림의 GpuNodeControl·GpuClusterPattern. v5 이하 스택은 알려 주지 않으므로 false·null 이다.
alter table tbl_aws_connection add column gpu_node_control boolean not null default false;
alter table tbl_aws_connection add column gpu_cluster_pattern varchar(100);
