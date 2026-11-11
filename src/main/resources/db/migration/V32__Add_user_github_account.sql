alter table tbl_user
    add column github_id bigint,
    add column github_login varchar(39);

alter table tbl_user
    add constraint tbl_user_github_id_key unique (github_id);
