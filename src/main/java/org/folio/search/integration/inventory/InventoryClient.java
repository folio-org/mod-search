package org.folio.search.integration.inventory;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.Instance;
import org.folio.search.model.service.ResultList;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class InventoryClient {

  /**
   * Retrieves resources by ids from inventory service.
   *
   * @param ids list of instance UUIDs.
   * @return {@link ResultList} with instance objects inside.
   */
  public ResultList<Instance> getInstances(List<String> ids) {
    //TODO: Reimplement this method using Feign client to retrieve instances
    log.debug("Fetching instances from inventory [ids: {}]", ids);
    return ResultList.empty();
  }
}
