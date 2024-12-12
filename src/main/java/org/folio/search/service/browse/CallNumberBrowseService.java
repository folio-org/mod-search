package org.folio.search.service.browse;

import static org.folio.search.utils.SearchUtils.CALL_NUMBER_TYPE_ID_FIELD;

import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.domain.dto.CallNumberBrowseItem;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.SearchResult;
import org.folio.search.model.index.CallNumberResource;
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
    return BrowseType.CALL_NUMBER;
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
        .volume(resource.volume())
        .chronology(resource.chronology())
        .enumeration(resource.enumeration())
        .copyNumber(resource.copyNumber())
        .isAnchor(isAnchor ? true : null)
        .totalRecords(getTotalRecords(ctx, resource, CallNumberResource::instances)));
  }

}
