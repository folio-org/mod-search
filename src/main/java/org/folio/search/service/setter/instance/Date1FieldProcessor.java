package org.folio.search.service.setter.instance;

import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Dates;
import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class Date1FieldProcessor implements FieldProcessor<Instance, Short> {

  private static final Pattern NUMERIC_REGEX = Pattern.compile("^\\d{1,4}$");
  private static final String ZERO = "0";
  private static final String ALPHA_U = "u";

  @Override
  public Short getFieldValue(Instance instance) {
    Dates dates = instance.getDates();
    if (dates != null && StringUtils.isNotEmpty(dates.getDate1())) {
      return normalizeDate1(dates.getDate1());
    }
    return 0;
  }

  public Short normalizeDate1(String value) {
    String date1 = value.replace(ALPHA_U, ZERO);
    var matcher = NUMERIC_REGEX.matcher(date1);
    if (matcher.find()) {
      return Short.valueOf(matcher.group());
    }
    return 0;
  }
}
