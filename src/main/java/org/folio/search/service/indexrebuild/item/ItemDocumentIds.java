package org.folio.search.service.indexrebuild.item;

import lombok.experimental.UtilityClass;

/**
 * Document id of an item is scoped by tenant, so the same item of different tenants of a consortium can coexist.
 */
@UtilityClass
public class ItemDocumentIds {

  public static String of(String itemId, String tenantId) {
    return itemId + "|" + tenantId;
  }
}
