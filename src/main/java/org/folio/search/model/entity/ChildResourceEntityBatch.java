package org.folio.search.model.entity;

import java.util.Collection;
import java.util.Map;

public record ChildResourceEntityBatch(Collection<Map<String, Object>> resourceEntities,
                                       Collection<Map<String, Object>> relationshipEntities) {

}
