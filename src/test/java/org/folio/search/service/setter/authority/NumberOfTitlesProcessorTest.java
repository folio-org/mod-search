package org.folio.search.service.setter.authority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.folio.search.client.EntitiesLinksClient;
import org.folio.search.domain.dto.Authority;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@UnitTest
@ExtendWith(MockitoExtension.class)
class NumberOfTitlesProcessorTest {

  @InjectMocks
  private NumberOfTitlesProcessor numberOfTitlesProcessor;
  @Mock
  private EntitiesLinksClient entitiesLinksClient;

  @Test
  void getFieldValue_positive() {
    var expectedSize = 3;
    var authorityId = UUID.randomUUID();
    var authority = new Authority();
    authority.setId(authorityId.toString());
    var idsList = List.of(authorityId);

    when(entitiesLinksClient.getLinksCount(EntitiesLinksClient.UuidCollection.of(idsList)))
      .thenReturn(ResponseEntity.of(Optional.of(EntitiesLinksClient.LinksCountCollection.of(
        List.of(EntitiesLinksClient.LinksCount.of(authorityId, expectedSize))))));

    var actual = numberOfTitlesProcessor.getFieldValue(authority);

    assertEquals(expectedSize, actual);
  }

  @Test
  void getFieldValue_positive_emptyValue() {
    var expectedSize = 0;
    var authorityId = UUID.randomUUID();
    var authority = new Authority();
    authority.setId(authorityId.toString());
    var idsList = List.of(authorityId);

    when(entitiesLinksClient.getLinksCount(EntitiesLinksClient.UuidCollection.of(idsList)))
      .thenReturn(ResponseEntity.of(Optional.of(EntitiesLinksClient.LinksCountCollection.of(Collections.emptyList()))));

    var actual = numberOfTitlesProcessor.getFieldValue(authority);

    assertEquals(expectedSize, actual);
  }
}
