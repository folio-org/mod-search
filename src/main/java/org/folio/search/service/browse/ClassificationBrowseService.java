package org.folio.search.service.browse;

import static org.folio.search.utils.SearchUtils.CLASSIFICATION_TYPE_ID_FIELD;

import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.domain.dto.ClassificationNumberBrowseItem;
import org.folio.search.model.BrowseResult;
import org.folio.search.model.SearchResult;
import org.folio.search.model.index.ClassificationResource;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.model.service.BrowseContext;
import org.folio.search.service.consortium.BrowseConfigServiceDecorator;
import org.folio.search.service.consortium.ConsortiumSearchHelper;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class ClassificationBrowseService
  extends AbstractShelvingOrderBrowseServiceBySearchAfter<ClassificationNumberBrowseItem, ClassificationResource> {

  protected ClassificationBrowseService(ConsortiumSearchHelper consortiumSearchHelper,
                                        BrowseConfigServiceDecorator configService) {
    super(consortiumSearchHelper, configService);
  }

  @Override
  protected String getValueForBrowsing(ClassificationNumberBrowseItem browseItem) {
    return browseItem.getClassificationNumber();
  }

  @Override
  protected BrowseType getBrowseType() {
    return BrowseType.INSTANCE_CLASSIFICATION;
  }

  @Override
  protected String getTypeIdField() {
    return CLASSIFICATION_TYPE_ID_FIELD;
  }

  @Override
  protected ClassificationNumberBrowseItem getEmptyBrowseItem(BrowseContext context) {
    return new ClassificationNumberBrowseItem()
      .classificationNumber(context.getAnchor())
      .totalRecords(0)
      .isAnchor(true);
  }

  @Override
  protected BrowseResult<ClassificationNumberBrowseItem> mapToBrowseResult(BrowseContext ctx,
                                                                           SearchResult<ClassificationResource> res,
                                                                           boolean isAnchor) {
    return BrowseResult.of(res)
      .map(resource -> {
        var subResources = consortiumSearchHelper.filterSubResourcesForConsortium(ctx, resource,
          ClassificationResource::instances);
        var totalRecords = getTotalRecords(subResources);
        var item = new ClassificationNumberBrowseItem()
          .classificationNumber(resource.number())
          .classificationTypeId(resource.typeId())
          .isAnchor(isAnchor ? true : null)
          .totalRecords(totalRecords);

        if (totalRecords == 1) {
          var subResource = subResources.iterator().next();
          item.setInstanceTitle(subResource.getInstanceTitle());
          item.setInstanceContributors(subResource.getInstanceContributors());
        }

        return item;
      });
  }

  private Integer getTotalRecords(Set<InstanceSubResource> subResources) {
    return subResources.stream()
      .map(InstanceSubResource::getCount)
      .reduce(0, Integer::sum);
  }
}
