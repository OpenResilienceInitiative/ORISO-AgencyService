package de.caritas.cob.agencyservice.api.admin.service.agency;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared normalization for local query tokens and owner-provided labels. */
final class AdminSearchText {
  private static final Pattern SPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

  private AdminSearchText() {}

  static List<String> tokens(String raw) {
    var tokens = raw == null ? List.<String>of() : SPACE.splitAsStream(raw)
        .map(AdminSearchText::fold).filter(s -> !s.isEmpty()).distinct().toList();
    return tokens.equals(List.of("*")) ? List.of() : tokens;
  }

  static String fold(String value) {
    return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
  }
}
