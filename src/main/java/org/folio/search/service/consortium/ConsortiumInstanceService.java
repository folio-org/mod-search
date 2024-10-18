package org.folio.search.service.consortium;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ConsortiumHolding;
import org.folio.search.domain.dto.ConsortiumHoldingCollection;
import org.folio.search.domain.dto.ConsortiumItem;
import org.folio.search.domain.dto.ConsortiumItemCollection;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.model.service.ConsortiumSearchContext;
import org.folio.search.model.types.ResourceType;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ConsortiumInstanceService {

  private final ConsortiumInstanceRepository repository;
  private final JsonConverter jsonConverter;


  public Instance fetchInstance(ConsortiumSearchContext context) {
    var searchInstanceQueryBuilder = new ConsortiumSearchQueryBuilder(context);
    var searchHoldingsQueryBuilder = new ConsortiumSearchQueryBuilder(context, ResourceType.HOLDINGS);
    var searchItemsQueryBuilder = new ConsortiumSearchQueryBuilder(context, ResourceType.ITEM);
    var instanceJson = repository.fetchJson(searchInstanceQueryBuilder);
    var holdingsJson = repository.fetchJson(searchHoldingsQueryBuilder);
    var itemsJson = repository.fetchJson(searchItemsQueryBuilder);
    if (instanceJson.isEmpty()) {
      throw new EntityNotFoundException("Instance not found");
    }

    var instance = jsonConverter.fromJson(instanceJson.get(0), Instance.class);
    var holdings = holdingsJson.stream().map(json -> jsonConverter.fromJson(json, Holding.class)).toList();
    var items = itemsJson.stream().map(json -> jsonConverter.fromJson(json, Item.class)).toList();
    instance.setHoldings(holdings);
    instance.setItems(items);

    return instance;
  }

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
