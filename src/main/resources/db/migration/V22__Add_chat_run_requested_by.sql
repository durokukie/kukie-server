-- 이 컬럼 이전에 만든 run 은 요청자를 알 수 없어 null 로 남는다.
alter table tbl_chat_run add column requested_by uuid references tbl_user(id);
