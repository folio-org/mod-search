package org.folio.search.service.setter.instance;

import java.util.List;
import java.util.Map;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Component;

@Component
public class ItemTagsProcessor extends AbstractTagsProcessor {

  /**
   * Used by dependency injection.
   *
   * @param jsonConverter {@link JsonConverter} bean
   */
  public ItemTagsProcessor(JsonConverter jsonConverter) {
    super(jsonConverter);
  }

  @Override
  protected List<String> getTags(Map<String, Object> eventBody) {
    return getTagsAsObjectFromValueByKey(eventBody, "items");
  }
}
