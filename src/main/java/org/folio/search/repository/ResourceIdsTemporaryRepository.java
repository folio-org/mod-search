package org.folio.search.repository;

import static java.lang.String.format;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
@RequiredArgsConstructor
public class ResourceIdsTemporaryRepository {

  private final JdbcTemplate jdbcTemplate;

  public void createTableForIds(String tableName) {
    jdbcTemplate.execute(format("CREATE TABLE IF NOT EXISTS %s (id VARCHAR(36) PRIMARY KEY NOT NULL);", tableName));
  }

  public void dropTableForIds(String tableName) {
    jdbcTemplate.execute(format("DROP TABLE IF EXISTS %s;", tableName));
  }

  public void insertIds(List<String> ids, String tableName) {
    jdbcTemplate.batchUpdate(format("INSERT INTO %s (id) VALUES (?) ON CONFLICT (id) DO NOTHING;", tableName),
      ids, ids.size(), (ps, argument) -> ps.setString(1, argument));
  }

  public void streamIds(String tableName, RowCallbackHandler rowCallbackHandler) {
    jdbcTemplate.query(format("SELECT * FROM %s", tableName), rowCallbackHandler);
  }
}
