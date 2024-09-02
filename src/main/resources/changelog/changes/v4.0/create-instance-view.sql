CREATE OR REPLACE VIEW instance_view AS
  SELECT h.tenant_id,
    i.id,
    jsonb_build_object('instanceId', i.id,
                        'instance', i.json,
                        'holdingsRecords', h.json,
                        'items', it.json,
                        'isBoundWith', i.is_bound_with) AS jsonb
  FROM instance i
    LEFT JOIN holding h
      on i.id = h.instance_id
    LEFT JOIN item it
      on h.id = it.holding_id;
