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

  private final JdbcTemplate jdbcTemplate;
  private final SystemProperties systemProperties;

  public void saveTenant(TenantEntity tenantEntity) {
    String query = "INSERT INTO " + systemProperties.getSchemaName() + ".known_tenant (id, central_id, active) "
                   + "VALUES (?, ?, ?) ON CONFLICT (id) DO UPDATE SET active = ?;";
    jdbcTemplate.update(query, tenantEntity.id(), tenantEntity.centralId(), tenantEntity.active(),
      tenantEntity.active());
  }

  public List<String> fetchDataTenantIds() {
    String query = "SELECT id FROM " + systemProperties.getSchemaName() + ".known_tenant "
                   + "WHERE active = TRUE AND central_id IS NULL;";
    return jdbcTemplate.query(query, (rs, rowNum) -> rs.getString("id"));
  }

}
