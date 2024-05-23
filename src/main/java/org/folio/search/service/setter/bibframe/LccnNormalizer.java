package org.folio.search.service.setter.bibframe;

import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class LccnNormalizer {
  private static final String NORMALIZED_LCCN_REGEX = "\\d{10}";
  private static final char HYPHEN = '-';

  /**
   * Normalizes the given LCCN value and returns the normalized LCCN.
   * If the given LCCN is invalid, an empty Optional is returned.
   *
   * @param lccn LCCN to be normalized
   * @return Returns the normalized LCCN. If the given LCCN is invalid, returns an empty Optional
   */
  public Optional<String> normalizeLccn(@NotNull final String lccn) {
    var normalizedLccn = lccn;

    // Remove white spaces
    normalizedLccn = normalizedLccn.replaceAll("\\s", StringUtils.EMPTY);

    // If lccn contains "/", remove it & all characters to the right of "/"
    normalizedLccn = normalizedLccn.replaceAll("/.*", StringUtils.EMPTY);

    // Process the serial number component of LCCN
    normalizedLccn = processSerialNumber(normalizedLccn);

    if (normalizedLccn.matches(NORMALIZED_LCCN_REGEX)) {
      return Optional.of(normalizedLccn);
    }

    log.warn("LCCN is not in expected format: [{}]", lccn);
    return Optional.empty();
  }

  /**
   * Serial number is demarcated by a hyphen (fifth character in the value). Further, the serial number must be six
   * digits in length. If fewer than six digits, remove the hyphen and left fill with zeroes so that there are six
   * digits in the serial number.
   */
  private String processSerialNumber(String lccn) {
    if (lccn.length() >= 5 && lccn.charAt(4) == HYPHEN) {
      var lccnParts = lccn.split(String.valueOf(HYPHEN));
      if (lccnParts.length == 2) {
        String prefix = lccnParts[0];
        StringBuilder serialNumber = new StringBuilder(lccnParts[1]);

        // Left fill the serial number with zeroes to make it six digits
        while (serialNumber.length() < 6) {
          serialNumber.insert(0, "0");
        }

        return serialNumber.insert(0, prefix).toString();
      }
    }
    return lccn;
  }
}
