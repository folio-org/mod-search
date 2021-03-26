package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.domain.dto.Tags;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Component;

@Component
public class InstanceTagsProcessor extends AbstractTagsProcessor {

  /**
   * Used by dependency injection.
   *
   * @param jsonConverter {@link JsonConverter} bean
   */
  public InstanceTagsProcessor(JsonConverter jsonConverter) {
    super(jsonConverter);
  }

  @Override
  protected List<String> getTags(Map<String, Object> eventBody) {
    return ofNullable(MapUtils.getObject(eventBody, "tags"))
      .map(tags -> jsonConverter.convert(tags, TAGS_TYPE_REFERENCE))
      .map(Tags::getTagList)
      .orElse(emptyList());
  }
}
