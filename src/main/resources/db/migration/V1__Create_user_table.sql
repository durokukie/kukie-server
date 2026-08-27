create table tbl_user(
    id uuid primary key,
    name varchar(50) not null,
    email varchar(255) not null unique,
    password varchar(255) not null,
    created_at timestamp not null,
    updated_at timestamp not null
)