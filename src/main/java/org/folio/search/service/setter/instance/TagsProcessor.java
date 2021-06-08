package org.folio.search.service.setter.instance;

import java.util.stream.Stream;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Tags;
import org.springframework.stereotype.Component;

@Component
public class TagsProcessor extends AbstractTagsProcessor {

  @Override
  protected Stream<Tags> getTags(Instance instance) {
    return Stream.of(instance.getTags());
  }
}
