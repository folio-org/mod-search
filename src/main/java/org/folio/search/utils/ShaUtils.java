package org.folio.search.utils;

import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;

import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ShaUtils {

  public static String sha(String... input) {
    return sha1Hex(Arrays.stream(input)
      .map(s -> s == null ? "" : s)
      .collect(Collectors.joining("|")));
  }
}
