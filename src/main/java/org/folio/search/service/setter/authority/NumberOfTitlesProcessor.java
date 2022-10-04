package org.folio.search.service.setter.authority;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.search.client.EntitiesLinksClient;
import org.folio.search.domain.dto.Authority;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class NumberOfTitlesProcessor implements FieldProcessor<Authority, Integer> {

  private final EntitiesLinksClient entitiesLinksClient;

  @Override
  public Integer getFieldValue(Authority authority) {
    var number = entitiesLinksClient.getLinksCount(EntitiesLinksClient.UuidCollection.of(
      List.of(UUID.fromString(authority.getId()))));
    var body = number.getBody();

    if (body == null || body.getLinks() == null || body.getLinks().isEmpty()) {
      return 0;
    }

    return body.getLinks().get(0).getTotalLinks();
  }
}
