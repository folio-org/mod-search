package org.folio.search.service.consortium;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ConsortiumHolding;
import org.folio.search.domain.dto.ConsortiumItem;
import org.folio.spring.FolioExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
@RequiredArgsConstructor
public class ConsortiumInstanceRepository {

  private final JdbcTemplate jdbcTemplate;
  private final FolioExecutionContext context;

  public List<ConsortiumHolding> fetchHoldings(ConsortiumSearchQueryBuilder searchQueryBuilder) {
    return jdbcTemplate.query(searchQueryBuilder.buildSelectQuery(context),
      (rs, rowNum) -> new ConsortiumHolding()
        .id(rs.getString("id"))
        .hrid(rs.getString("hrid"))
        .tenantId(rs.getString("tenantId"))
        .instanceId(rs.getString("instanceId"))
        .callNumberPrefix(rs.getString("callNumberPrefix"))
        .callNumber(rs.getString("callNumber"))
        .callNumberSuffix(rs.getString("callNumberSuffix"))
        .copyNumber(rs.getString("copyNumber"))
        .permanentLocationId(rs.getString("permanentLocationId"))
        .discoverySuppress(rs.getBoolean("discoverySuppress")),
      searchQueryBuilder.getQueryArguments()
    );
  }

  public Integer count(ConsortiumSearchQueryBuilder searchQueryBuilder) {
    return jdbcTemplate.queryForObject(searchQueryBuilder.buildCountQuery(context),
      Integer.class, searchQueryBuilder.getQueryArguments());
  }

  public List<ConsortiumItem> fetchItems(ConsortiumSearchQueryBuilder searchQueryBuilder) {
    return jdbcTemplate.query(searchQueryBuilder.buildSelectQuery(context),
      (rs, rowNum) -> new ConsortiumItem()
        .id(rs.getString("id"))
        .hrid(rs.getString("hrid"))
        .tenantId(rs.getString("tenantId"))
        .instanceId(rs.getString("instanceId"))
        .holdingsRecordId(rs.getString("holdingsRecordId"))
        .barcode(rs.getString("barcode")),
      searchQueryBuilder.getQueryArguments()
    );
  }

}
