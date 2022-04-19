package org.folio.search.repository;

import static java.lang.String.format;

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

  public void insertId(String id, String tableName) {
    jdbcTemplate.execute(format("INSERT INTO %s (id) VALUES ('%s') ON CONFLICT (id) DO NOTHING;", tableName, id));
  }

  public void streamIds(String tableName, RowCallbackHandler rowCallbackHandler) {
    jdbcTemplate.query(format("SELECT * FROM %s", tableName), rowCallbackHandler);
  }

}
