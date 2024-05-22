package org.folio.search.service.consortium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.folio.search.converter.ConsortiumHoldingMapper;
import org.folio.search.converter.ConsortiumItemMapper;
import org.folio.search.domain.dto.ConsortiumHolding;
import org.folio.search.domain.dto.ConsortiumHoldingCollection;
import org.folio.search.domain.dto.ConsortiumItem;
import org.folio.search.domain.dto.ConsortiumItemCollection;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.service.SearchService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ConsortiumInstanceSearchServiceTest {

  private @Mock ConsortiumHoldingMapper holdingMapper;
  private @Mock ConsortiumItemMapper itemMapper;
  private @Mock SearchService searchService;
  private @InjectMocks ConsortiumInstanceSearchService service;

  @BeforeEach
  void setUpMocks() {
    lenient().doAnswer(invocationOnMock -> {
      var instanceId = (String) invocationOnMock.getArgument(0);
      var holding = (Holding) invocationOnMock.getArgument(1);
      return new ConsortiumHolding()
        .id(holding.getId())
        .instanceId(instanceId)
        .tenantId(holding.getTenantId());
    }).when(holdingMapper).map(anyString(), any());
    lenient().doAnswer(invocationOnMock -> {
      var instanceId = (String) invocationOnMock.getArgument(0);
      var item = (Item) invocationOnMock.getArgument(1);
      return new ConsortiumItem()
        .id(item.getId())
        .instanceId(instanceId)
        .tenantId(item.getTenantId())
        .holdingsRecordId(item.getHoldingsRecordId());
    }).when(itemMapper).map(anyString(), any());
  }

  @Test
  void getConsortiumHolding_positive() {
    var instanceId = UUID.randomUUID().toString();
    var id = UUID.randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var holdings = List.of(holding(UUID.randomUUID().toString(), CENTRAL_TENANT_ID), holding(id, MEMBER_TENANT_ID));
    var searchResult = SearchResult.of(1, List.of(new Instance().id(instanceId).holdings(holdings)));

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumHolding(id, request);

    assertThat(result.getId()).isEqualTo(id);
    assertThat(result.getTenantId()).isEqualTo(MEMBER_TENANT_ID);
    assertThat(result.getInstanceId()).isEqualTo(instanceId);
  }

  @Test
  void getConsortiumHolding_positive_noRecords() {
    var id = UUID.randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var searchResult = SearchResult.of(0, Collections.<Instance>emptyList());

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumHolding(id, request);

    assertThat(result).isEqualTo(new ConsortiumHolding());
  }

  @Test
  void getConsortiumHolding_positive_noHoldings() {
    var instanceId = UUID.randomUUID().toString();
    var id = UUID.randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var holdings = Collections.<Holding>emptyList();
    var searchResult = SearchResult.of(1, List.of(new Instance().id(instanceId).holdings(holdings)));

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumHolding(id, request);

    assertThat(result).isEqualTo(new ConsortiumHolding());
  }

  @Test
  void getConsortiumHolding_positive_noDesiredHolding() {
    var instanceId = UUID.randomUUID().toString();
    var id = UUID.randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var holdings = List.of(holding(UUID.randomUUID().toString(), CENTRAL_TENANT_ID));
    var searchResult = SearchResult.of(1, List.of(new Instance().id(instanceId).holdings(holdings)));

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumHolding(id, request);

    assertThat(result).isEqualTo(new ConsortiumHolding());
  }

  @Test
  void getConsortiumItem_positive() {
    var instanceId = UUID.randomUUID().toString();
    var id = UUID.randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var items = List.of(item(UUID.randomUUID().toString(), CENTRAL_TENANT_ID), item(id, MEMBER_TENANT_ID));
    var searchResult = SearchResult.of(1, List.of(new Instance().id(instanceId).items(items)));

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumItem(id, request);

    assertThat(result.getId()).isEqualTo(id);
    assertThat(result.getTenantId()).isEqualTo(MEMBER_TENANT_ID);
    assertThat(result.getInstanceId()).isEqualTo(instanceId);
    assertThat(result.getHoldingsRecordId()).isNotBlank();
  }

  @Test
  void getConsortiumItem_positive_noRecords() {
    var id = UUID.randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var searchResult = SearchResult.of(0, Collections.<Instance>emptyList());

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumItem(id, request);

    assertThat(result).isEqualTo(new ConsortiumItem());
  }

  @Test
  void getConsortiumItem_positive_noItems() {
    var instanceId = UUID.randomUUID().toString();
    var id = UUID.randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var items = Collections.<Item>emptyList();
    var searchResult = SearchResult.of(1, List.of(new Instance().id(instanceId).items(items)));

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumItem(id, request);

    assertThat(result).isEqualTo(new ConsortiumItem());
  }

  @Test
  void getConsortiumItem_positive_noDesiredItem() {
    var instanceId = UUID.randomUUID().toString();
    var id = UUID.randomUUID().toString();
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var items = List.of(item(UUID.randomUUID().toString(), CENTRAL_TENANT_ID));
    var searchResult = SearchResult.of(1, List.of(new Instance().id(instanceId).items(items)));

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.getConsortiumItem(id, request);

    assertThat(result).isEqualTo(new ConsortiumItem());
  }

  @Test
  void fetchConsortiumBatchHoldings_positive() {
    var ids = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var instances = List.of(
      instanceForHoldings(List.of(holding(UUID.randomUUID().toString(), CENTRAL_TENANT_ID),
        holding(ids.get(0), MEMBER_TENANT_ID))),
      instanceForHoldings(List.of(holding(ids.get(1), CENTRAL_TENANT_ID))),
      instanceForHoldings(List.of(holding(UUID.randomUUID().toString(), CENTRAL_TENANT_ID))));
    var searchResult = SearchResult.of(instances.size(), instances);
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var expected = new ConsortiumHoldingCollection()
      .holdings(instances.subList(0, instances.size() - 1).stream().map(instance -> {
        var holding = instance.getHoldings().size() > 1 ? instance.getHoldings().get(1) : instance.getHoldings().get(0);
        return new ConsortiumHolding()
          .id(holding.getId())
          .instanceId(instance.getId())
          .tenantId(holding.getTenantId());
      }).toList())
      .totalRecords(2);

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.fetchConsortiumBatchHoldings(request, Sets.newHashSet(ids));

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void fetchConsortiumBatchItems_positive() {
    var ids = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    var instances = List.of(
      instanceForItems(List.of(item(UUID.randomUUID().toString(), CENTRAL_TENANT_ID),
        item(ids.get(0), MEMBER_TENANT_ID))),
      instanceForItems(List.of(item(ids.get(1), CENTRAL_TENANT_ID))),
      instanceForItems(List.of(item(UUID.randomUUID().toString(), CENTRAL_TENANT_ID))));
    var searchResult = SearchResult.of(instances.size(), instances);
    var request = Mockito.<CqlSearchRequest<Instance>>mock();
    var expected = new ConsortiumItemCollection()
      .items(instances.subList(0, instances.size() - 1).stream().map(instance -> {
        var item = instance.getItems().size() > 1 ? instance.getItems().get(1) : instance.getItems().get(0);
        return new ConsortiumItem()
          .id(item.getId())
          .instanceId(instance.getId())
          .tenantId(item.getTenantId())
          .holdingsRecordId(item.getHoldingsRecordId());
      }).toList())
      .totalRecords(2);

    when(searchService.search(request)).thenReturn(searchResult);

    var result = service.fetchConsortiumBatchItems(request, Sets.newHashSet(ids));

    assertThat(result).isEqualTo(expected);
  }

  private Holding holding(String id, String tenantId) {
    return new Holding()
      .id(id)
      .tenantId(tenantId);
  }

  private Item item(String id, String tenantId) {
    return new Item()
      .id(id)
      .tenantId(tenantId)
      .holdingsRecordId(UUID.randomUUID().toString());
  }

  private Instance instanceForHoldings(List<Holding> holdings) {
    return new Instance()
      .id(UUID.randomUUID().toString())
      .holdings(holdings);
  }

  private Instance instanceForItems(List<Item> items) {
    return new Instance()
      .id(UUID.randomUUID().toString())
      .items(items);
  }
}
