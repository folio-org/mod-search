package org.folio.search.service.consortium;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ConsortiumHolding;
import org.folio.search.domain.dto.ConsortiumHoldingCollection;
import org.folio.search.domain.dto.ConsortiumItem;
import org.folio.search.domain.dto.ConsortiumItemCollection;
import org.folio.search.model.service.ConsortiumSearchContext;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ConsortiumInstanceService {

  private final ConsortiumInstanceRepository repository;

  public ConsortiumHoldingCollection fetchHoldings(ConsortiumSearchContext context) {
    var searchQueryBuilder = new ConsortiumSearchQueryBuilder(context);
    List<ConsortiumHolding> holdingList = repository.fetchHoldings(searchQueryBuilder);
    return new ConsortiumHoldingCollection().holdings(holdingList).totalRecords(repository.count(searchQueryBuilder));
  }

  public ConsortiumItemCollection fetchItems(ConsortiumSearchContext context) {
    var searchQueryBuilder = new ConsortiumSearchQueryBuilder(context);
    List<ConsortiumItem> itemList = repository.fetchItems(searchQueryBuilder);
    return new ConsortiumItemCollection().items(itemList).totalRecords(repository.count(searchQueryBuilder));
  }
}
