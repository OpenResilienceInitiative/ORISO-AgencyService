package de.caritas.cob.agencyservice.api.admin.service.legal;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The closed set of {@code {{key}}} placeholders ORISO understands in Träger-authored legal text
 * (ADR-021 decisions 5 and 6), plus the two operations the product needs on them: surviving
 * sanitisation, and being substituted.
 *
 * <h2>Why the dialect is {@code {{key}}} and not {@code ${key}}</h2>
 *
 * <p>The existing DPP placeholders ({@code responsible}, {@code dataProtectionOfficer}) are
 * rendered by Freemarker, where {@code ${...}} can invoke methods on the objects in the data model.
 * That is acceptable for platform-authored templates and unacceptable for text a Träger types into
 * an editor. A literal {@code {{key}}} replacement cannot reach any object by construction, so
 * Träger-authored text never goes near {@link
 * de.caritas.cob.agencyservice.api.service.TemplateRenderer}. The two dialects coexist on purpose;
 * unifying them is deliberately deferred (ADR-021 decision 6).
 *
 * <h2>The sanitiser collision — measured 2026-08-16</h2>
 *
 * <p>OWASP's {@code java-html-sanitizer} rewrites any {@code &#123;&#123;} in a text node to
 * {@code &#123;<!-- -->&#123;}. That is a deliberate defence: it stops sanitised HTML from being
 * re-interpreted as a mustache template by a client-side engine (AngularJS and friends). The side
 * effect is that {@code &#123;&#123;legal_links&#125;&#125;} typed correctly by an admin comes back
 * out broken — it would fail the publication validator on valid input, and render as visible braces
 * to a help-seeker.
 *
 * <p>{@link #restoreKnownTokens} therefore un-breaks <b>only the tokens on this allowlist</b>, and
 * only in their exact {@code &#123;&#123;key&#125;&#125;} form. Undoing the comment injection
 * wholesale would hand every Träger the ability to smuggle an arbitrary mustache expression into
 * whatever renders the text; restoring three known keys does not, because the substitution of those
 * three is something the platform performs itself.
 */
public final class LegalTextTokens {

  /** The mandatory token: the clickable links the client substitutes (ADR-021 decision 2). */
  public static final String LEGAL_LINKS = "legal_links";

  /** Beratungsstelle name — substituted server-side (ADR-021 decision 5). */
  public static final String BERATUNGSSTELLE = "Beratungsstelle";

  /** Fachbereich / topic name — substituted server-side (ADR-021 decision 5). */
  public static final String THEMA = "Thema";

  /** Rendered form of the token that must be present in any published consent text. */
  public static final String LEGAL_LINKS_TOKEN = token(LEGAL_LINKS);

  private static final List<String> KNOWN_KEYS = List.of(LEGAL_LINKS, BERATUNGSSTELLE, THEMA);

  /** Exactly what the OWASP sanitizer leaves behind, for the known keys only. */
  private static final Pattern SPLIT_BY_SANITIZER =
      Pattern.compile("\\{<!-- -->\\{(" + String.join("|", KNOWN_KEYS) + ")}}");

  private LegalTextTokens() {}

  /** {@code {{key}}} — the one place the syntax is spelled out. */
  public static String token(String key) {
    return "{{" + key + "}}";
  }

  /**
   * Repairs the {@code &#123;<!-- -->&#123;} the OWASP sanitizer produces, for the known keys only.
   * Any other mustache stays split, which is exactly the protection the sanitizer intended.
   */
  public static String restoreKnownTokens(String sanitizedHtml) {
    if (sanitizedHtml == null || sanitizedHtml.isEmpty()) {
      return sanitizedHtml;
    }
    return SPLIT_BY_SANITIZER.matcher(sanitizedHtml).replaceAll(match -> "{{" + match.group(1) + "}}");
  }

  /**
   * HTML-escapes a value that is about to be substituted into already-sanitised legal HTML.
   *
   * <p>Substitution happens <em>after</em> {@code LegalContentSanitizer} has run, so the value
   * never passes the OWASP allowlist. An agency name is admin-authored, but admin-authored is not
   * the same as safe: legal content is rendered into the page as HTML (CONTEXT-legal-documents
   * records {@code AnonymousConsentGate} doing so via {@code dangerouslySetInnerHTML}), so an
   * unescaped name containing markup would be stored XSS reaching every help-seeker of that
   * Beratungsstelle. Escaping here is the only point where that can still be prevented.
   *
   * <p>Explicit and exhaustive rather than delegated: the five characters below are the complete
   * set that can break out of HTML text or an attribute value, and keeping the list visible is
   * worth more here than reusing a general-purpose escaper.
   */
  public static String escapeForHtml(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  /**
   * Replaces the given tokens with their values. A plain literal replacement: no expression
   * language, no method calls, nothing a Träger can steer.
   *
   * <p>Values are inserted verbatim — callers substituting into HTML must pass them through
   * {@link #escapeForHtml} first.
   *
   * <p>Tokens absent from {@code values} are left standing. That is how {@code
   * &#123;&#123;legal_links&#125;&#125;} survives the server side and reaches the client, which is
   * the only party that knows the deployment's link targets.
   */
  public static String substitute(String text, Map<String, String> values) {
    if (text == null || text.isEmpty() || values == null || values.isEmpty()) {
      return text;
    }
    var result = text;
    for (var entry : values.entrySet()) {
      if (entry.getValue() != null) {
        // String#replace is a literal replacement - no regex, so no group references in the value.
        result = result.replace(token(entry.getKey()), entry.getValue());
      }
    }
    return result;
  }
}
