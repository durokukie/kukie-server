alter table tbl_connector_credential
    add column previous_token_hash varchar(64),
    add column previous_expires_at timestamp,
    add column rotated_at timestamp;
