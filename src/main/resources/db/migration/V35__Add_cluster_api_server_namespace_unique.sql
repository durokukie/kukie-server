alter table tbl_cluster
    add constraint uk_cluster_team_api_server_namespace unique (team_id, api_server, default_namespace);
