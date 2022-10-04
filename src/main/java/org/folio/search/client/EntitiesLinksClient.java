package org.folio.search.client;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("instance-authority-links")
public interface EntitiesLinksClient {

  @PostMapping(value = "/links/authorities/bulk/count", consumes = APPLICATION_JSON_VALUE)
  ResponseEntity<LinksCountCollection> getLinksCount(@RequestBody UuidCollection authorityIdCollection);

  @Data
  @AllArgsConstructor(staticName = "of")
  class UuidCollection {
    private List<UUID> ids;
  }

  @Data
  @AllArgsConstructor(staticName = "of")
  class LinksCountCollection {
    private List<LinksCount> links;
  }

  @Data
  @AllArgsConstructor(staticName = "of")
  class LinksCount {
    private UUID id;
    private Integer totalLinks;
  }
}
