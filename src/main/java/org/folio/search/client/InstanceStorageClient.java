package org.folio.search.client;

import org.folio.search.domain.dto.ReindexJob;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient("instance-storage")
public interface InstanceStorageClient {
  @PostMapping(path = "/reindex")
  ReindexJob submitReindex();
}
