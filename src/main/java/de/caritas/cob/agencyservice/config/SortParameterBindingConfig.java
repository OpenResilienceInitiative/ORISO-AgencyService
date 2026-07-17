package de.caritas.cob.agencyservice.config;

import de.caritas.cob.agencyservice.api.model.Sort;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Binds the {@code field} query parameter of {@code GET /agencyadmin/agencies} leniently. The
 * admin frontend sends upper-cased field names without separators (e.g. {@code POSTCODE},
 * {@code CREATEDATE}), while the generated {@link Sort.FieldEnum} constants are underscore
 * separated ({@code POST_CODE}, {@code CREATE_DATE}) — the default enum binding only accepts
 * exact constant names and rejects those requests with HTTP 400.
 */
@Component
public class SortParameterBindingConfig implements WebMvcConfigurer {

  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(String.class, Sort.FieldEnum.class, new LenientSortFieldConverter());
  }

  static class LenientSortFieldConverter implements Converter<String, Sort.FieldEnum> {

    @Override
    public Sort.FieldEnum convert(@NonNull String source) {
      var canonicalSource = canonical(source);
      for (var candidate : Sort.FieldEnum.values()) {
        if (canonical(candidate.name()).equals(canonicalSource)
            || canonical(candidate.getValue()).equals(canonicalSource)) {
          return candidate;
        }
      }
      throw new IllegalArgumentException("Unexpected sort field '" + source + "'");
    }

    private static String canonical(String value) {
      return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }
  }
}
