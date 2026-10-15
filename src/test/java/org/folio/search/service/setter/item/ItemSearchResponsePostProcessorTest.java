package org.folio.search.service.setter.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.ItemSearchRecord;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.consortium.TenantProvider;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ItemSearchResponsePostProcessorTest {

  private static final String TENANT = "tenant1";
  private static final String INSTANCE_ID = "instance-1";

  @Mock
  private SearchRepository searchRepository;
  @Mock
  private FolioExecutionContext context;
  @Mock
  private TenantProvider tenantProvider;
  @Mock
  private SearchResponse searchResponse;
  @Mock
  private SearchHits searchHits;
  @Mock
  private SearchHit searchHit;

  @InjectMocks
  private ItemSearchResponsePostProcessor postProcessor;

  @BeforeEach
  void setUp() {
    lenient().when(context.getTenantId()).thenReturn(TENANT);
    lenient().when(tenantProvider.getTenant(TENANT)).thenReturn(TENANT);
    lenient().when(searchRepository.search(any(), any())).thenReturn(searchResponse);
    lenient().when(searchResponse.getHits()).thenReturn(searchHits);
    lenient().when(searchHits.getHits()).thenReturn(new SearchHit[] {searchHit});
  }

  @Test
  void getGeneric_shouldReturnItemClass() {
    assertThat(postProcessor.getGeneric()).isEqualTo(ItemSearchRecord.class);
  }

  @Test
  void process_shouldDoNothingWhenItemsAreNullOrEmpty() {
    postProcessor.process(null);
    postProcessor.process(List.of());

    verifyNoInteractions(searchRepository);
  }

  @Test
  void process_shouldDoNothingWhenItemsHaveNoInstanceId() {
    postProcessor.process(List.of(new ItemSearchRecord().id("item-1")));

    verifyNoInteractions(searchRepository);
  }

  @Test
  void process_shouldSetInstanceTitleOnMatchingItems() {
    when(searchHit.getId()).thenReturn(INSTANCE_ID);
    when(searchHit.getSourceAsMap()).thenReturn(Map.of("plain_title", "Some title"));
    var item1 = new ItemSearchRecord().id("item-1").instanceId(INSTANCE_ID);
    var item2 = new ItemSearchRecord().id("item-2").instanceId(INSTANCE_ID);
    var otherItem = new ItemSearchRecord().id("item-3").instanceId("instance-2");

    postProcessor.process(List.of(item1, item2, otherItem));

    assertThat(item1.getInstanceTitle()).isEqualTo("Some title");
    assertThat(item2.getInstanceTitle()).isEqualTo("Some title");
    assertThat(otherItem.getInstanceTitle()).isNull();
  }
}
