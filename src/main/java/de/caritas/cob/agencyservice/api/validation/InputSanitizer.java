package de.caritas.cob.agencyservice.api.validation;

import org.owasp.html.HtmlPolicyBuilder;
import org.springframework.stereotype.Component;

/**
 * OWASP-backed HTML sanitizer for user-supplied legal text (e.g. a Fachbereich's data privacy
 * policy). Mirrors ORISO-TenantService's {@code InputSanitizer} so both services strip the same
 * classes of markup before persisting rich text authored in the admin panel's editor.
 */
@Component
public class InputSanitizer {

  public String sanitize(String input) {
    var sanitizer = new HtmlPolicyBuilder().toFactory();
    return sanitizer.sanitize(input);
  }

  public String sanitizeAllowingFormatting(String input) {
    var sanitizer =
        new HtmlPolicyBuilder()
            .allowStyling()
            .allowCommonInlineFormattingElements()
            .allowCommonBlockElements()
            .toFactory();
    return sanitizer.sanitize(input);
  }

  public String sanitizeAllowingFormattingAndLinks(String input) {
    var sanitizer =
        new HtmlPolicyBuilder()
            .allowStyling()
            .allowStandardUrlProtocols()
            .allowCommonInlineFormattingElements()
            .allowCommonBlockElements()
            .allowElements("a")
            .allowAttributes("href", "target")
            .onElements("a")
            .allowElements("img")
            .allowAttributes("src", "width", "height")
            .onElements("img")
            .toFactory();
    return sanitizer.sanitize(input);
  }
}
