-- 연결 한 줄은 AWS 계정 하나다. 한 팀이 같은 계정을 두 번 연결하지 못하게 계정 칸으로 막는다.
alter table tbl_aws_connection drop constraint uk_aws_connection_team;
create unique index uk_aws_connection_team_account on tbl_aws_connection (team_id, account_id) where account_id is not null;
