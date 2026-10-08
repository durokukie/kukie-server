update tbl_user set email = lower(trim(email)) where email <> lower(trim(email));
