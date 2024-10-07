package org.folio.search.service.setter.instance;

import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class Date1FieldProcessor implements FieldProcessor<Instance, Short> {

  private static final int MAX_LENGTH = 4;
  private static final String ZERO = "0";
  private static final String NON_NUMERIC_REGEX = "\\D";

  @Override
  public Short getFieldValue(Instance instance) {
    var dates = instance.getDates();
    if (dates != null && StringUtils.isNotEmpty(dates.getDate1())) {
      return normalizeDate1(dates.getDate1());
    }
    return 0;
  }

  public Short normalizeDate1(String value) {
    var date1 = value.replaceAll(NON_NUMERIC_REGEX, ZERO);
    if (date1.length() <= MAX_LENGTH) {
      return Short.valueOf(date1);
    }
    return 0;
  }
}
