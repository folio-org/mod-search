package org.folio.search.service.setter.authority;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.folio.search.client.EntitiesLinksClient;
import org.folio.search.domain.dto.Authority;
import org.folio.search.service.setter.PostProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class NumberOfTitlesPostProcessor implements PostProcessor {

  private final EntitiesLinksClient entitiesLinksClient;

  @Override
  public void process(List<Object> res) {
    if (res == null || res.isEmpty() || !(res.get(0) instanceof Authority)) {
      return;
    }

    var authorizedAuthorities = ((List<Authority>) (Object) res).stream()
      .filter(a -> a.getAuthRefType().equals("Authorized"))
      .collect(toList());

    var authorityIds = authorizedAuthorities.stream()
      .map(Authority::getId)
      .map(UUID::fromString)
      .collect(toList());

    var numbersOfTitles = entitiesLinksClient.getLinksCount(
      EntitiesLinksClient.UuidCollection.of(authorityIds)).getBody();

    IntStream.range(0, authorizedAuthorities.size())
      .forEach(i -> authorizedAuthorities.get(i).setNumberOfTitles(numbersOfTitles.getLinks().get(i).getTotalLinks()));
  }
}
