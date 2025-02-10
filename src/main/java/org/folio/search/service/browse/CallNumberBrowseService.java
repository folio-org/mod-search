package org.folio.search.service.browse;

import static org.folio.search.utils.SearchUtils.CALL_NUMBER_TYPE_ID_FIELD;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.SearchResult;
import org.folio.search.model.index.CallNumberResource;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.service.consortium.BrowseConfigServiceDecorator;
import org.folio.search.service.consortium.ConsortiumSearchHelper;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class CallNumberBrowseService
  extends AbstractShelvingOrderBrowseServiceBySearchAfter<CallNumberBrowseItem, CallNumberResource> {

  protected CallNumberBrowseService(ConsortiumSearchHelper consortiumSearchHelper,
                                    BrowseConfigServiceDecorator configService) {
    super(consortiumSearchHelper, configService);
  }

  @Override
  protected String getValueForBrowsing(CallNumberBrowseItem browseItem) {
    return browseItem.getFullCallNumber();
  }

  @Override
  protected BrowseType getBrowseType() {
    return BrowseType.INSTANCE_CALL_NUMBER;
  }

  @Override
  protected String getTypeIdField() {
    return CALL_NUMBER_TYPE_ID_FIELD;
  }

  @Override
  protected CallNumberBrowseItem getEmptyBrowseItem(BrowseContext context) {
    return new CallNumberBrowseItem()
      .fullCallNumber(context.getAnchor())
      .totalRecords(0)
      .isAnchor(true);
  }

  @Override
  protected BrowseResult<CallNumberBrowseItem> mapToBrowseResult(BrowseContext ctx,
                                                                 SearchResult<CallNumberResource> res,
                                                                 boolean isAnchor) {
    return BrowseResult.of(res)
      .map(resource -> new CallNumberBrowseItem()
        .fullCallNumber(resource.fullCallNumber())
        .callNumber(resource.callNumber())
        .callNumberPrefix(resource.callNumberPrefix())
        .callNumberSuffix(resource.callNumberSuffix())
        .callNumberTypeId(resource.callNumberTypeId())
        .instanceTitle(getInstanceTitle(ctx, resource, CallNumberResource::instances))
        .isAnchor(isAnchor ? true : null)
        .totalRecords(getTotalRecords(ctx, resource, CallNumberResource::instances)));
  }

  private String getInstanceTitle(BrowseContext ctx, CallNumberResource resource,
                                  Function<CallNumberResource, Set<InstanceSubResource>> func) {
    var instanceSubResources = consortiumSearchHelper.filterSubResourcesForConsortium(ctx, resource, func);
    if (instanceSubResources.size() == 1) {
      return instanceSubResources.iterator().next().getInstanceTitle();
    }
    return null;
  }

  @Override
  protected Integer getTotalRecords(BrowseContext ctx, CallNumberResource resource,
                                    Function<CallNumberResource, Set<InstanceSubResource>> func) {
    return consortiumSearchHelper.filterSubResourcesForConsortium(ctx, resource, func)
      .stream()
      .map(InstanceSubResource::getInstanceId)
      .map(List::size)
      .reduce(0, Integer::sum);
  }
}
