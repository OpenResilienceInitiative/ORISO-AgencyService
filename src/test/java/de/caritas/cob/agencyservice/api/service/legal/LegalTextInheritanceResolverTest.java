package de.caritas.cob.agencyservice.api.service.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalText;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextLevel;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextVersion;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextVersionRepository;
import de.caritas.cob.agencyservice.api.service.ApplicationSettingsService;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.ApplicationSettingsDTO;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.FeatureToggleDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.Content;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ADR-021 decision 9: the whole department → agency → Träger → platform-operator chain in one
 * place. These tests walk each rung and, just as importantly, pin what makes a rung fall through.
 */
@ExtendWith(MockitoExtension.class)
class LegalTextInheritanceResolverTest {

  @Mock private LegalTextVersionRepository legalTextVersionRepository;
  @Mock private TenantService tenantService;
  @Mock private ApplicationSettingsService applicationSettingsService;

  @InjectMocks private LegalTextInheritanceResolver resolver;

  @BeforeEach
  void setUp() {
    lenient()
        .when(
            legalTextVersionRepository
                .findFirstByOwnerLevelAndOwnerIdAndKindAndSupersededAtIsNullOrderByIdDesc(
                    any(), any(), any()))
        .thenReturn(Optional.empty());
  }

  private void singleDomainMultitenancy(boolean enabled) {
    ReflectionTestUtils.setField(resolver, "multitenancyWithSingleDomain", enabled);
  }

  private void tenantMayOverridePlatformText(boolean allowed) {
    when(applicationSettingsService.getApplicationSettings())
        .thenReturn(
            new ApplicationSettingsDTO()
                .legalContentChangesBySingleTenantAdminsAllowed(
                    new FeatureToggleDTO().value(allowed)));
  }

  private Agency agency(String dpp, String imprint, String consent) {
    return Agency.builder()
        .id(7L)
        .name("Zentrum")
        .consultingTypeId(1)
        .tenantId(3L)
        .contentDpp(dpp)
        .contentImprint(imprint)
        .consentText(consent)
        .build();
  }

  private AgencyTopic department(Agency agency) {
    return AgencyTopic.builder().id(4711L).topicId(42L).agency(agency).build();
  }

  private RestrictedTenantDTO tenantWith(String privacyHtml) {
    return new RestrictedTenantDTO()
        .id(3L)
        .name("Träger")
        .content(new Content().impressum("").privacyLanguages(Map.of("de", privacyHtml)));
  }

  // ---------------------------------------------------------------- level 4

  @Test
  void resolveDpp_Should_useTheDepartmentText_When_itIsPublished() {
    var department = department(agency("{\"de\":\"agency\"}", null, null));
    department.setContentDpp("{\"de\":\"department\"}");
    department.setConsentText("{\"de\":\"Ich habe die {{legal_links}} gelesen.\"}");
    department.setPublicationStatus(PublicationStatus.PUBLISHED);
    when(legalTextVersionRepository
            .findFirstByOwnerLevelAndOwnerIdAndKindAndSupersededAtIsNullOrderByIdDesc(
                LegalTextLevel.DEPARTMENT, 4711L, LegalTextKind.DPP))
        .thenReturn(Optional.of(LegalTextVersion.builder().id(100L).build()));

    var resolved = resolver.resolveDpp(department);

    assertThat(resolved.content()).contains("department");
    assertThat(resolved.consentText()).contains("{{legal_links}}");
    assertThat(resolved.sourceLevel()).isEqualTo(LegalTextSourceLevel.DEPARTMENT);
    assertThat(resolved.versionId()).isEqualTo(100L);
    // Rung 4 answered, so nothing above it is consulted at all.
    verify(tenantService, never()).getRestrictedTenantDataByTenantId(any());
  }

  @Test
  void resolveDpp_Should_fallThroughADraft_ratherThanBlankingOutTheDocument() {
    var department = department(agency("{\"de\":\"agency\"}", null, null));
    department.setContentDpp("{\"de\":\"still drafting\"}");
    department.setPublicationStatus(PublicationStatus.DRAFT);

    var resolved = resolver.resolveDpp(department);

    // A Fachbereich mid-edit must not remove a legally required document from its help-seekers.
    assertThat(resolved.content()).contains("agency").doesNotContain("still drafting");
    assertThat(resolved.sourceLevel()).isEqualTo(LegalTextSourceLevel.AGENCY);
  }

  @Test
  void resolveDpp_Should_preferAReferencedSharedObject_overTheInlineColumn() {
    var department = department(agency("{\"de\":\"agency\"}", null, null));
    department.setContentDpp("{\"de\":\"stale inline leftover\"}");
    department.setPublicationStatus(PublicationStatus.PUBLISHED);
    department.setDpp(
        LegalText.builder()
            .id(55L)
            .kind(LegalTextKind.DPP)
            .label("Standard-DSE")
            .content("{\"de\":\"shared\"}")
            .consentText("{\"de\":\"shared consent {{legal_links}}\"}")
            .publicationStatus(PublicationStatus.PUBLISHED)
            .build());
    when(legalTextVersionRepository
            .findFirstByOwnerLevelAndOwnerIdAndKindAndSupersededAtIsNullOrderByIdDesc(
                LegalTextLevel.SHARED, 55L, LegalTextKind.DPP))
        .thenReturn(Optional.of(LegalTextVersion.builder().id(200L).build()));

    var resolved = resolver.resolveDpp(department);

    // ADR-014: once assigned, the shared object fully replaces the inline column - which is what
    // makes the 0026 backfill leftovers harmless.
    assertThat(resolved.content()).contains("shared");
    assertThat(resolved.consentText()).contains("shared consent");
    assertThat(resolved.versionId()).isEqualTo(200L);
  }

  @Test
  void resolveDpp_Should_fallThrough_When_theReferencedSharedObjectIsStillADraft() {
    var department = department(agency("{\"de\":\"agency\"}", null, null));
    department.setDpp(
        LegalText.builder()
            .id(55L)
            .kind(LegalTextKind.DPP)
            .label("Entwurf")
            .content("{\"de\":\"shared draft\"}")
            .publicationStatus(PublicationStatus.DRAFT)
            .build());

    assertThat(resolver.resolveDpp(department).sourceLevel())
        .isEqualTo(LegalTextSourceLevel.AGENCY);
  }

  // ---------------------------------------------------------------- level 3

  @Test
  void resolveDpp_Should_useTheAgencyText_WithoutAnyPublicationStatus() {
    var agency = agency("{\"de\":\"agency\"}", null, "{\"de\":\"agency consent {{legal_links}}\"}");
    when(legalTextVersionRepository
            .findFirstByOwnerLevelAndOwnerIdAndKindAndSupersededAtIsNullOrderByIdDesc(
                LegalTextLevel.AGENCY, 7L, LegalTextKind.DPP))
        .thenReturn(Optional.of(LegalTextVersion.builder().id(300L).build()));

    var resolved = resolver.resolveDpp(department(agency));

    // "What is stored, applies" - there is no draft state on this level to fall through.
    assertThat(resolved.sourceLevel()).isEqualTo(LegalTextSourceLevel.AGENCY);
    assertThat(resolved.consentText()).contains("agency consent");
    assertThat(resolved.versionId()).isEqualTo(300L);
  }

  // ---------------------------------------------------------------- levels 2 and 1

  @Test
  void resolveDpp_Should_useTheTraegerText_When_aTraegerMayReplaceThePlatformText() {
    singleDomainMultitenancy(true);
    tenantMayOverridePlatformText(true);
    when(tenantService.getRestrictedTenantDataByTenantId(3L))
        .thenReturn(tenantWith("<p>Träger-DSE</p>"));

    var resolved = resolver.resolveDpp(department(agency(null, null, null)));

    assertThat(resolved.sourceLevel()).isEqualTo(LegalTextSourceLevel.TENANT);
    assertThat(resolved.content()).contains("Träger-DSE");
    // Träger and platform histories live in ORISO-TenantService, not here.
    assertThat(resolved.versionId()).isNull();
  }

  @Test
  void resolveDpp_Should_useThePlatformText_When_aTraegerMayNotReplaceIt() {
    singleDomainMultitenancy(true);
    tenantMayOverridePlatformText(false);
    when(tenantService.getMainTenant()).thenReturn(tenantWith("<p>Plattform-DSE</p>"));

    var resolved = resolver.resolveDpp(department(agency(null, null, null)));

    // The toggle decides level 1 vs level 2; when it is off the Träger's own text is not consulted.
    assertThat(resolved.sourceLevel()).isEqualTo(LegalTextSourceLevel.PLATFORM);
    assertThat(resolved.content()).contains("Plattform-DSE");
    verify(tenantService, never()).getRestrictedTenantDataByTenantId(any());
  }

  @Test
  void resolveDpp_Should_returnTheTenantTextAsALanguageMap_EvenWhenOnlyTheFlatFieldIsSet() {
    singleDomainMultitenancy(false);
    when(tenantService.getRestrictedTenantDataByTenantId(3L))
        .thenReturn(
            new RestrictedTenantDTO()
                .id(3L)
                .name("Träger")
                .content(new Content().impressum("").privacy("<p>nur Deutsch</p>")));

    var resolved = resolver.resolveDpp(department(agency(null, null, null)));

    // Every level below stores a language->HTML map; handing callers two shapes depending on which
    // rung answered would push the multilingual pick back onto the client.
    assertThat(resolved.content()).startsWith("{").contains("nur Deutsch");
    assertThat(resolved.sourceLevel()).isEqualTo(LegalTextSourceLevel.TENANT);
  }

  @Test
  void resolveDpp_Should_degradeToAGap_When_tenantServiceIsUnreachable() {
    singleDomainMultitenancy(false);
    when(tenantService.getRestrictedTenantDataByTenantId(3L))
        .thenThrow(new IllegalStateException("tenant service down"));

    var resolved = resolver.resolveDpp(department(agency(null, null, null)));

    // A neighbouring service being down must not turn a help-seeker's request for a privacy policy
    // into a 500 - the caller sees a gap it can react to.
    assertThat(resolved.sourceLevel()).isEqualTo(LegalTextSourceLevel.NONE);
    assertThat(resolved.isPresent()).isFalse();
  }

  @Test
  void resolveDpp_Should_returnNone_When_nothingIsAuthoredAnywhere() {
    singleDomainMultitenancy(false);
    when(tenantService.getRestrictedTenantDataByTenantId(3L))
        .thenReturn(new RestrictedTenantDTO().id(3L).name("Träger"));

    assertThat(resolver.resolveDpp(department(agency(null, null, null))))
        .isEqualTo(ResolvedLegalText.none());
  }

  // ---------------------------------------------------------------- imprint

  @Test
  void resolveImprint_Should_neverCarryAConsentSentence() {
    var agency = agency(null, "{\"de\":\"Impressum\"}", "{\"de\":\"consent {{legal_links}}\"}");

    var resolved = resolver.resolveImprint(department(agency));

    // ADR-021 decision 7: the imprint is an information duty, never a consent gate. Carrying the
    // policy's sentence here would invite a second, meaningless tick.
    assertThat(resolved.content()).contains("Impressum");
    assertThat(resolved.consentText()).isNull();
  }

  @Test
  void resolve_Should_returnNone_When_thereIsNoDepartment() {
    assertThat(resolver.resolveDpp(null)).isEqualTo(ResolvedLegalText.none());
    assertThat(resolver.resolveImprint(null)).isEqualTo(ResolvedLegalText.none());
  }
}
