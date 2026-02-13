package org.folio.search.service.reindex.jdbc;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.search.configuration.properties.SystemProperties;
import org.folio.search.model.entity.TenantEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TenantRepository {

  public static final String INSERT_QUERY = """
    INSERT INTO %s.known_tenant (id, central_id, active)
    VALUES (?, ?, ?) ON CONFLICT (id) DO UPDATE SET active = ?;
    """;

  public static final String FETCH_QUERY = """
    SELECT id FROM %s.known_tenant
    WHERE active = TRUE AND central_id IS NULL;
    """;

  private final JdbcTemplate jdbcTemplate;
  private final SystemProperties systemProperties;

  @SuppressWarnings("java:S2077")
  public void saveTenant(TenantEntity tenantEntity) {
    String query = INSERT_QUERY.formatted(systemProperties.getSchemaName());
    jdbcTemplate.update(query, tenantEntity.id(), tenantEntity.centralId(), tenantEntity.active(),
      tenantEntity.active());
  }

  @SuppressWarnings("java:S2077")
  public List<String> fetchDataTenantIds() {
    String query = FETCH_QUERY.formatted(systemProperties.getSchemaName());
    return jdbcTemplate.query(query, (rs, rowNum) -> rs.getString("id"));
  }
}
