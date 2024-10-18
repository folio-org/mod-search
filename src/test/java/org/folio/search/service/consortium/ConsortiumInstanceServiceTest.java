package org.folio.search.service.consortium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.util.Collections;
import java.util.List;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.model.service.ConsortiumSearchContext;
import org.folio.search.model.types.ResourceType;
import org.folio.search.sample.SampleInstances;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.TestUtils;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ConsortiumInstanceServiceTest {

  private @Spy JsonConverter jsonConverter = new JsonConverter(new ObjectMapper());
  private @Mock ConsortiumInstanceRepository searchRepository;
  private @InjectMocks ConsortiumInstanceService service;

  @Test
  void getConsortiumInstance_positive() {
    var searchContext = ConsortiumSearchContext.builderFor(ResourceType.INSTANCE).filter("instanceId", "test").build();
    var instance = TestUtils.readJsonFromFile("/samples/semantic-web-primer/instance.json", Instance.class);
    var holdings = TestUtils.readJsonFromFile("/samples/semantic-web-primer/holdings.json",
      new TypeReference<List<Holding>>() {
      });
    var items = TestUtils.readJsonFromFile("/samples/semantic-web-primer/items.json", new TypeReference<List<Item>>() {
    });
    when(searchRepository.fetchJson(any()))
      .thenReturn(List.of(TestUtils.asJsonString(instance)))
      .thenReturn(holdings.stream().map(TestUtils::asJsonString).toList())
      .thenReturn(items.stream().map(TestUtils::asJsonString).toList());

    var result = service.fetchInstance(searchContext);

    assertThat(result.getId()).isEqualTo(SampleInstances.getSemanticWebId());
    assertThat(result.getItems()).hasSize(items.size());
    assertThat(result.getHoldings()).hasSize(holdings.size());
  }

  @Test
  void getConsortiumInstance_throwsNotFoundWhenNoInstance() {
    var searchContext = ConsortiumSearchContext.builderFor(ResourceType.INSTANCE).filter("instanceId", "test").build();
    when(searchRepository.fetchJson(any()))
      .thenReturn(Collections.emptyList());

    assertThatThrownBy(() -> service.fetchInstance(searchContext)).isInstanceOf(EntityNotFoundException.class);
  }
}
