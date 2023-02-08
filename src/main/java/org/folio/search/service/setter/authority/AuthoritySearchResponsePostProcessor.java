package org.folio.search.service.setter.authority;

import static java.util.stream.Collectors.toMap;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.client.EntitiesLinksClient;
import org.folio.search.domain.dto.Authority;
import org.folio.search.service.setter.SearchResponsePostProcessor;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public final class AuthoritySearchResponsePostProcessor implements SearchResponsePostProcessor<Authority> {

  private final EntitiesLinksClient entitiesLinksClient;

  @Override
  public Class<Authority> getGeneric() {
    return Authority.class;
  }

  @Override
  public void process(List<Authority> res) {
    log.debug("process:: by [res: {}]", collectionToLogMsg(res, true));

    if (res == null || res.isEmpty()) {
      return;
    }

    var authorizedAuthorities = res.stream()
      .filter(a -> a.getAuthRefType().equals("Authorized"))
      .toList();

    var authorityIds = authorizedAuthorities.stream()
      .map(Authority::getId)
      .map(UUID::fromString)
      .toList();

    var response = entitiesLinksClient.getLinksCount(
        EntitiesLinksClient.UuidCollection.of(authorityIds)).getBody();

    if (response == null || response.getLinks() == null) {
      return;
    }
    var numbersOfTitles = response.getLinks().stream()
      .collect(toMap(EntitiesLinksClient.LinksCount::getId, EntitiesLinksClient.LinksCount::getTotalLinks,
        (id1, id2) -> id1));

    authorizedAuthorities.forEach(a -> {
      var numberOfTitles = numbersOfTitles.get(UUID.fromString(a.getId()));
      a.setNumberOfTitles(numberOfTitles == null ? 0 : numberOfTitles);
    });
  }
}
