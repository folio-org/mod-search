package org.folio.search.service.consortium;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.converter.ConsortiumHoldingMapper;
import org.folio.search.converter.ConsortiumItemMapper;
import org.folio.search.domain.dto.ConsortiumHolding;
import org.folio.search.domain.dto.ConsortiumHoldingCollection;
import org.folio.search.domain.dto.ConsortiumItem;
import org.folio.search.domain.dto.ConsortiumItemCollection;
import org.folio.search.domain.dto.Instance;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.service.SearchService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;

/**
 * Class designed to be executed only in scope of consortium central tenant id.
 * So, it can be expected to always have central tenant id in {@link FolioExecutionContext}.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ConsortiumInstanceSearchService {

  private final SearchService searchService;

  public ConsortiumHolding getConsortiumHolding(String id, CqlSearchRequest<Instance> searchRequest) {
    var result = searchService.search(searchRequest);

    if (isEmpty(result.getRecords()) || isEmpty(result.getRecords().iterator().next().getHoldings())) {
      return new ConsortiumHolding();
    }

    var instance = result.getRecords().iterator().next();
    var holding = instance.getHoldings().stream()
      .filter(hol -> Objects.equals(id, hol.getId()))
      .findFirst().orElse(null);

    if (holding == null) {
      return new ConsortiumHolding();
    }

    return ConsortiumHoldingMapper.toConsortiumHolding(instance.getId(), holding);
  }

  public ConsortiumItem getConsortiumItem(String id, CqlSearchRequest<Instance> searchRequest) {
    var result = searchService.search(searchRequest);

    if (isEmpty(result.getRecords()) || isEmpty(result.getRecords().iterator().next().getItems())) {
      return new ConsortiumItem();
    }

    var instance = result.getRecords().iterator().next();
    var item = instance.getItems().stream()
      .filter(it -> Objects.equals(id, it.getId()))
      .findFirst().orElse(null);

    if (item == null) {
      return new ConsortiumItem();
    }

    return ConsortiumItemMapper.toConsortiumItem(instance.getId(), item);
  }

  public ConsortiumHoldingCollection fetchConsortiumBatchHoldings(CqlSearchRequest<Instance> searchRequest,
                                                                  Set<String> ids) {
    var result = searchService.search(searchRequest);
    var consortiumHoldings = result.getRecords().stream()
      .flatMap(instance ->
        instance.getHoldings().stream()
          .filter(holding -> ids.contains(holding.getId()))
          .map(holding -> ConsortiumHoldingMapper.toConsortiumHolding(instance.getId(), holding))
      )
      .toList();
    return new ConsortiumHoldingCollection()
      .holdings(consortiumHoldings)
      .totalRecords(consortiumHoldings.size());
  }

  public ConsortiumItemCollection fetchConsortiumBatchItems(CqlSearchRequest<Instance> searchRequest,
                                                            Set<String> ids) {
    var result = searchService.search(searchRequest);
    var consortiumItems = result.getRecords().stream()
      .flatMap(instance ->
        instance.getItems().stream()
          .filter(item -> ids.contains(item.getId()))
          .map(item -> ConsortiumItemMapper.toConsortiumItem(instance.getId(), item))
      )
      .toList();

    return new ConsortiumItemCollection()
      .items(consortiumItems)
      .totalRecords(consortiumItems.size());
  }
}
