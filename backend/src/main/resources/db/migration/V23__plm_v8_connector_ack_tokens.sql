UPDATE plm_connector_registry
SET config_json = '{"transport":"stub","target":"ERP","ackToken":"ERP-ACK-TOKEN"}',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'conn_plm_erp_sync'
  AND (config_json IS NULL OR config_json NOT LIKE '%"ackToken"%');

UPDATE plm_connector_registry
SET config_json = '{"transport":"stub","target":"MES","ackToken":"MES-ACK-TOKEN"}',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'conn_plm_mes_sync'
  AND (config_json IS NULL OR config_json NOT LIKE '%"ackToken"%');

UPDATE plm_connector_registry
SET config_json = '{"transport":"stub","target":"PDM","ackToken":"PDM-ACK-TOKEN"}',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'conn_plm_pdm_release'
  AND (config_json IS NULL OR config_json NOT LIKE '%"ackToken"%');

UPDATE plm_connector_registry
SET config_json = '{"transport":"stub","target":"CAD","ackToken":"CAD-ACK-TOKEN"}',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'conn_plm_cad_publish'
  AND (config_json IS NULL OR config_json NOT LIKE '%"ackToken"%');
