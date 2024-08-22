package org.folio.search.service.setter.instance;

import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Dates;
import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class Date1FieldProcessor implements FieldProcessor<Instance, String> {

  private static final Pattern FOUR_DIGIT_REGEX = Pattern.compile("^\\d{4}$");
  private static final String ZERO = "0";
  private static final String ALPHA_U = "u";

  @Override
  public String getFieldValue(Instance instance) {
    Dates dates = instance.getDates();
    if (dates != null && StringUtils.isNotEmpty(dates.getDate1())) {
      return normalizeDate1(dates.getDate1());
    }
    return ZERO;
  }

  public String normalizeDate1(String value) {
    String date1 = value.replaceAll(ALPHA_U, ZERO);
    var matcher = FOUR_DIGIT_REGEX.matcher(date1);
    if (matcher.find()) {
      return matcher.group();
    }
    return ZERO;
  }
}
