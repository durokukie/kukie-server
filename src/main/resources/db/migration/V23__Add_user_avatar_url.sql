-- OAuth 로그인 때 받은 프로필 사진 주소. 비밀번호로만 가입한 사용자는 null 이다.
alter table tbl_user add column avatar_url varchar(500);
