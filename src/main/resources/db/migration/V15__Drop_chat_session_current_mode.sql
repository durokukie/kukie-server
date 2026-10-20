-- DURO-100 정리 2단계. V14 에서 not null 을 풀고 코드는 이 칸을 더 쓰지 않는다 (kukie-server#69).
alter table tbl_chat_session drop column current_mode;
