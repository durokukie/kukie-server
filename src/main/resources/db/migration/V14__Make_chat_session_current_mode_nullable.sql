-- DURO-100 으로 모드가 없어져 current_mode 를 더 쓰지 않는다.
-- 배포 중 새 코드(칸을 모름)와 옛 코드(칸을 채움)가 같이 저장할 수 있게 먼저 not null 만 푼다. 칸은 다음 마이그레이션에서 지운다.
alter table tbl_chat_session alter column current_mode drop not null;
