package org.folio.search.service.setter.instance;

import static java.util.stream.Collectors.toSet;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.CollectionUtils;
import org.springframework.stereotype.Component;

@Component
public class StatisticalCodesProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    var result = new HashSet<String>();
    result.addAll(getStatisticalCodesList(Stream.of(instance), Instance::getStatisticalCodeIds));
    result.addAll(getStatisticalCodesList(toStreamSafe(instance.getHoldings()), Holding::getStatisticalCodeIds));
    result.addAll(getStatisticalCodesList(toStreamSafe(instance.getItems()), Item::getStatisticalCodeIds));
    return result;
  }

  private static <T> Set<String> getStatisticalCodesList(Stream<T> objectsStream, Function<T, List<String>> func) {
    return objectsStream.filter(Objects::nonNull)
      .map(func).flatMap(CollectionUtils::toStreamSafe)
      .filter(StringUtils::isNotBlank).collect(toSet());
  }
}
