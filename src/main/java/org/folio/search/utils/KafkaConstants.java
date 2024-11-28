package org.folio.search.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class KafkaConstants {

  public static final String AUTHORITY_LISTENER_ID = "mod-search-authorities-listener";
  public static final String EVENT_LISTENER_ID = "mod-search-events-listener";
  public static final String CLASSIFICATION_TYPE_LISTENER_ID = "mod-search-classification-type-listener";
  public static final String CALL_NUMBER_TYPE_LISTENER_ID = "mod-search-call-number-type-listener";
  public static final String LOCATION_LISTENER_ID = "mod-search-location-listener";
  public static final String LINKED_DATA_LISTENER_ID = "mod-search-linked-data-listener";
  public static final String REINDEX_RANGE_INDEX_LISTENER_ID = "mod-search-reindex-index-listener";
  public static final String REINDEX_RECORDS_LISTENER_ID = "mod-search-reindex-records-listener";
  public static final String INSTANCE_SUB_RESOURCE_LISTENER_ID = "mod-search-instance-sub-resource-listener";

}
