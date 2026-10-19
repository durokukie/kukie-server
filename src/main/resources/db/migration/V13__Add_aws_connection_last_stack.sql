alter table tbl_aws_connection add column last_account_id varchar(12);
alter table tbl_aws_connection add column last_stack_id varchar(512);
alter table tbl_aws_connection add column last_stack_deleted_at timestamp;
