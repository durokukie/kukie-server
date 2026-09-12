-- 초대는 7일 뒤 만료된다. 기존 초대는 만든 시각 기준으로 만료 시각을 채운다.
alter table tbl_team_invitation add column expires_at timestamp;
update tbl_team_invitation set expires_at = created_at + interval '7 days';
alter table tbl_team_invitation alter column expires_at set not null;
