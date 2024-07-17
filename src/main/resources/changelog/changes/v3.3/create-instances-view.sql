CREATE OR REPLACE VIEW instances_view AS
  SELECT h.tenant_id,
    i.instance_id,
    jsonb_build_object('instanceId', i.instance_id,
                        'instance', i.instance_json,
                        'holdingsRecords', h.holding_json,
                        'items', it.item_json,
                        'isBoundWith', i.is_bound_with) AS jsonb
  FROM instances i
    LEFT JOIN holdings h
      on i.instance_id = h.instance_id
    LEFT JOIN items it
      on h.holding_id = it.holding_id;
