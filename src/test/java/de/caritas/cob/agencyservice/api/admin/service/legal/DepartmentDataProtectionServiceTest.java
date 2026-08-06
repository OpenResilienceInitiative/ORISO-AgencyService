package de.caritas.cob.agencyservice.api.admin.service.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.exception.httpresponses.AgencyAccessDeniedException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalText;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.api.validation.InputSanitizer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepartmentDataProtectionServiceTest {

  @Mock private AgencyTopicRepository agencyTopicRepository;
  @Mock private AuthenticatedUser authenticatedUser;
  @Mock private UserAdminService userAdminService;

  private DepartmentDataProtectionService service;

  @BeforeEach
  void setUp() {
    TenantContext.clear(); // no ThreadLocal tenant leaks into the tenant guard
    // real sanitizer so we actually verify markup stripping, not a mock
    service =
        new DepartmentDataProtectionService(
            agencyTopicRepository,
            new LegalContentSanitizer(new InputSanitizer()),
            authenticatedUser,
            userAdminService);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private AgencyTopic existingDepartment() {
    var department = AgencyTopic.builder().topicId(42L).build();
    when(agencyTopicRepository.findByAgency_IdAndTopicId(7L, 42L))
        .thenReturn(Optional.of(department));
    return department;
  }

  private AgencyTopic existingDepartmentInTenant(Long agencyTenantId) {
    var department =
        AgencyTopic.builder()
            .topicId(42L)
            .agency(
                Agency.builder()
                    .id(7L)
                    .name("Test-Zentrum")
                    .consultingTypeId(1)
                    .tenantId(agencyTenantId)
                    .build())
            .build();
    when(agencyTopicRepository.findByAgency_IdAndTopicId(7L, 42L))
        .thenReturn(Optional.of(department));
    return department;
  }

  private LegalText sharedDpp() {
    return LegalText.builder()
        .id(100L)
        .kind(LegalTextKind.DPP)
        .label("Geteilte DSE")
        .content("{\"de\":\"<p>alt</p>\"}")
        .publicationStatus(PublicationStatus.PUBLISHED)
        .build();
  }

  @Test
  void publish_Should_breakSharedLink_And_storeOwnText_When_departmentReferencesOne() {
    // ADR-014 amendment 2026-07-28: publishing under one Fachbereich must NOT rewrite the shared
    // object — the 0026 backfill merged byte-identical departments onto one row, so writing through
    // would silently republish every sibling department. Publishing means "this department leaves
    // the shared text": clear the reference, store the text on the department itself.
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    var department = existingDepartment();
    var referenced = sharedDpp();
    department.setDpp(referenced);

    var status =
        service.publishDepartmentDataPrivacy(7L, 42L, Map.of("de", "<p>neu</p>"), true);

    assertThat(status).isEqualTo(PublicationStatus.PUBLISHED);

    var saved = ArgumentCaptor.forClass(AgencyTopic.class);
    verify(agencyTopicRepository).save(saved.capture());
    assertThat(saved.getValue().getDpp()).as("shared link must be broken").isNull();
    assertThat(saved.getValue().getContentDpp()).contains("neu");
    assertThat(saved.getValue().getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);

    // the sibling departments still pointing at this row must be byte-identical afterwards
    assertThat(referenced.getContent()).isEqualTo("{\"de\":\"<p>alt</p>\"}");
    assertThat(referenced.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
  }

  @Test
  void draftSave_Should_keepSharedLink_And_parkDraftInline_When_departmentReferencesOne() {
    // A draft must not change what the public sees: the reference stays, so the read path keeps
    // resolving the inherited text while the draft waits in the otherwise-unused inline column.
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    var department = existingDepartment();
    var referenced = sharedDpp();
    department.setDpp(referenced);

    var status =
        service.publishDepartmentDataPrivacy(7L, 42L, Map.of("de", "<p>entwurf</p>"), false);

    assertThat(status).isEqualTo(PublicationStatus.DRAFT);

    var saved = ArgumentCaptor.forClass(AgencyTopic.class);
    verify(agencyTopicRepository).save(saved.capture());
    assertThat(saved.getValue().getDpp()).as("draft must not unshare").isSameAs(referenced);
    assertThat(saved.getValue().getContentDpp()).contains("entwurf");
    assertThat(saved.getValue().getPublicationStatus()).isEqualTo(PublicationStatus.DRAFT);
    assertThat(referenced.getContent()).isEqualTo("{\"de\":\"<p>alt</p>\"}");
  }

  @Test
  void read_Should_returnReferencedLegalText_When_departmentReferencesOne_AndHasNoPendingDraft() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    var department = existingDepartment();
    department.setDpp(
        LegalText.builder()
            .id(100L)
            .kind(LegalTextKind.DPP)
            .label("Geteilte DSE")
            .content("{\"de\":\"<p>geteilt</p>\"}")
            .publicationStatus(PublicationStatus.PUBLISHED)
            .build());

    var view = service.getDepartmentDataPrivacy(7L, 42L);

    assertThat(view.content()).isEqualTo("{\"de\":\"<p>geteilt</p>\"}");
    assertThat(view.publicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
  }

  @Test
  void read_Should_returnPendingDraft_When_departmentHasDraftAlongsideReference() {
    // The editor must show the admin their own unfinished work, not the inherited text they are
    // about to replace — while the public still resolves the reference.
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    var department = existingDepartment();
    department.setContentDpp("{\"de\":\"<p>entwurf</p>\"}");
    department.setPublicationStatus(PublicationStatus.DRAFT);
    department.setDpp(
        LegalText.builder()
            .id(100L)
            .kind(LegalTextKind.DPP)
            .label("Geteilte DSE")
            .content("{\"de\":\"<p>geteilt</p>\"}")
            .publicationStatus(PublicationStatus.PUBLISHED)
            .build());

    var view = service.getDepartmentDataPrivacy(7L, 42L);

    assertThat(view.content()).isEqualTo("{\"de\":\"<p>entwurf</p>\"}");
    assertThat(view.publicationStatus()).isEqualTo(PublicationStatus.DRAFT);
  }

  @Test
  void publish_Should_sanitizeStoreAsJsonMap_andSetPublished_ForFullAgencyAdmin() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    existingDepartment();

    var status =
        service.publishDepartmentDataPrivacy(
            7L,
            42L,
            Map.of("de", "<p onclick=\"steal()\">Datenschutz <script>bad()</script></p>"),
            true);

    // a full admin is never scoped-checked against agency ids
    verifyNoInteractions(userAdminService);
    assertThat(status).isEqualTo(PublicationStatus.PUBLISHED);

    var saved = ArgumentCaptor.forClass(AgencyTopic.class);
    verify(agencyTopicRepository).save(saved.capture());
    assertThat(saved.getValue().getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
    var storedJson = saved.getValue().getContentDpp();
    assertThat(storedJson).startsWith("{").contains("\"de\":");
    // dangerous markup removed, text kept
    assertThat(storedJson).contains("Datenschutz").doesNotContain("script").doesNotContain("onclick");
    assertThat(saved.getValue().getUpdateDate()).isNotNull();
  }

  @Test
  void publish_Should_setDraft_When_publishFalse() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    existingDepartment();

    var status =
        service.publishDepartmentDataPrivacy(7L, 42L, Map.of("de", "<p>Entwurf</p>"), false);

    assertThat(status).isEqualTo(PublicationStatus.DRAFT);
  }

  @Test
  void publish_Should_allow_When_restrictedAdminOwnsTheAgency() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);
    when(authenticatedUser.requireUserId()).thenReturn("admin-1");
    when(userAdminService.getAdminUserAgencyIds("admin-1")).thenReturn(List.of(7L, 9L));
    existingDepartment();

    var status =
        service.publishDepartmentDataPrivacy(7L, 42L, Map.of("de", "<p>ok</p>"), true);

    assertThat(status).isEqualTo(PublicationStatus.PUBLISHED);
    verify(agencyTopicRepository).save(any());
  }

  @Test
  void publish_Should_throwAccessDenied_When_restrictedAdminDoesNotOwnTheAgency() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);
    when(authenticatedUser.requireUserId()).thenReturn("admin-1");
    when(userAdminService.getAdminUserAgencyIds("admin-1")).thenReturn(List.of(9L));

    assertThatExceptionOfType(AgencyAccessDeniedException.class)
        .isThrownBy(
            () ->
                service.publishDepartmentDataPrivacy(7L, 42L, Map.of("de", "<p>x</p>"), true));

    // IDOR guard runs before any load or write
    verify(agencyTopicRepository, never()).findByAgency_IdAndTopicId(any(), any());
    verify(agencyTopicRepository, never()).save(any());
  }

  @Test
  void publish_Should_throwNotFound_When_departmentMissing() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    when(agencyTopicRepository.findByAgency_IdAndTopicId(7L, 99L)).thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(
            () ->
                service.publishDepartmentDataPrivacy(7L, 99L, Map.of("de", "<p>x</p>"), true));
    verify(agencyTopicRepository, never()).save(any());
  }

  @Test
  void getDepartmentDataPrivacy_Should_returnStoredContentAndStatus() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    var department = existingDepartment();
    department.setContentDpp("{\"de\":\"<p>DSE</p>\"}");
    department.setPublicationStatus(PublicationStatus.PUBLISHED);

    var view = service.getDepartmentDataPrivacy(7L, 42L);

    assertThat(view.content()).isEqualTo("{\"de\":\"<p>DSE</p>\"}");
    assertThat(view.publicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
  }

  @Test
  void getDepartmentDataPrivacy_Should_throwAccessDenied_When_restrictedAdminDoesNotOwnTheAgency() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);
    when(authenticatedUser.requireUserId()).thenReturn("admin-1");
    when(userAdminService.getAdminUserAgencyIds("admin-1")).thenReturn(List.of(9L));

    assertThatExceptionOfType(AgencyAccessDeniedException.class)
        .isThrownBy(() -> service.getDepartmentDataPrivacy(7L, 42L));
    verify(agencyTopicRepository, never()).findByAgency_IdAndTopicId(any(), any());
  }

  @Test
  void getDepartmentDataPrivacy_Should_throwNotFound_When_departmentMissing() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    when(agencyTopicRepository.findByAgency_IdAndTopicId(7L, 99L)).thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.getDepartmentDataPrivacy(7L, 99L));
  }

  @Test
  void publish_Should_throwAccessDenied_When_fullAdminEditsAnotherTenantsAgency() {
    // full admin (not restricted) of tenant 1 tries to edit an agency belonging to tenant 2
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    when(authenticatedUser.getTenantId()).thenReturn(1L);
    existingDepartmentInTenant(2L);

    assertThatExceptionOfType(AgencyAccessDeniedException.class)
        .isThrownBy(
            () ->
                service.publishDepartmentDataPrivacy(7L, 42L, Map.of("de", "<p>x</p>"), true));
    verify(agencyTopicRepository, never()).save(any());
  }

  @Test
  void publish_Should_allow_When_fullAdminEditsOwnTenantsAgency() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    when(authenticatedUser.getTenantId()).thenReturn(1L);
    existingDepartmentInTenant(1L);

    var status =
        service.publishDepartmentDataPrivacy(7L, 42L, Map.of("de", "<p>ok</p>"), true);

    assertThat(status).isEqualTo(PublicationStatus.PUBLISHED);
    verify(agencyTopicRepository).save(any());
  }

  @Test
  void publish_Should_keepAllowedFormattingAndLinks() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    var department = existingDepartment();

    service.publishDepartmentDataPrivacy(
        7L, 42L, Map.of("de", "<strong>Wichtig</strong> <a href=\"https://caritas.de\">Info</a>"),
        true);

    // guards against a regression to the strip-everything sanitize() policy
    var stored = department.getContentDpp();
    assertThat(stored).contains("<strong>").contains("Wichtig");
    assertThat(stored).contains("href").contains("https://caritas.de");
  }

  @Test
  void publish_Should_storeAllLanguagesInJsonMap() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    var department = existingDepartment();

    service.publishDepartmentDataPrivacy(
        7L, 42L, Map.of("de", "<p>Datenschutz</p>", "en", "<p>Privacy</p>"), true);

    var stored = department.getContentDpp();
    assertThat(stored).contains("\"de\":").contains("Datenschutz");
    assertThat(stored).contains("\"en\":").contains("Privacy");
  }

  @Test
  void publish_Should_overwriteContent_andFlipPublishedBackToDraft_When_republishedAsDraft() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    var department = existingDepartment();

    service.publishDepartmentDataPrivacy(7L, 42L, Map.of("de", "<p>final</p>"), true);
    assertThat(department.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);

    // a second call as draft overwrites the content and reverts the status
    var status =
        service.publishDepartmentDataPrivacy(7L, 42L, Map.of("de", "<p>revised</p>"), false);

    assertThat(status).isEqualTo(PublicationStatus.DRAFT);
    assertThat(department.getPublicationStatus()).isEqualTo(PublicationStatus.DRAFT);
    assertThat(department.getContentDpp()).contains("revised").doesNotContain("final");
  }

  @Test
  void publish_Should_storeEmptyJsonObject_When_contentIsNull() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    var department = existingDepartment();

    var status = service.publishDepartmentDataPrivacy(7L, 42L, null, true);

    assertThat(status).isEqualTo(PublicationStatus.PUBLISHED);
    assertThat(department.getContentDpp()).isEqualTo("{}");
  }

  @Test
  void publish_Should_coerceNullTranslationValueToEmptyString() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    var department = existingDepartment();
    var content = new java.util.HashMap<String, String>();
    content.put("de", null);

    service.publishDepartmentDataPrivacy(7L, 42L, content, true);

    assertThat(department.getContentDpp()).isEqualTo("{\"de\":\"\"}");
  }

  @Test
  void publish_Should_passThroughMetaSuffixedKeysUnsanitized() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    var department = existingDepartment();
    // translation-metadata convention: __meta-suffixed keys carry JSON, not HTML - sanitising
    // them would corrupt the payload (quotes -> HTML entities -> invalid JSON), so they are
    // passed through verbatim after a strict schema validation instead
    var metaJson = "{\"mt\":true,\"src\":\"de\",\"at\":\"2026-07-04T10:00:00Z\"}";

    service.publishDepartmentDataPrivacy(
        7L, 42L, Map.of("de", "<p>Datenschutz</p>", "de__meta", metaJson), true);

    var stored = department.getContentDpp();
    assertThat(stored).contains("\"de__meta\":");
    // the JSON payload survives verbatim (an HTML sanitizer would have escaped the quotes away)
    assertThat(stored).contains("src").contains("2026-07-04T10:00:00Z");
    assertThat(stored).doesNotContain("&#34;").doesNotContain("&quot;");
  }

  @Test
  void publish_Should_acceptValidMetaSuffixedKey_andPersist() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    existingDepartment();

    var status =
        service.publishDepartmentDataPrivacy(
            7L, 42L, Map.of("de__meta", "{\"mt\":true,\"src\":\"de\",\"at\":\"2026-07-04\"}"), true);

    assertThat(status).isEqualTo(PublicationStatus.PUBLISHED);
    verify(agencyTopicRepository).save(any());
  }

  @Test
  void publish_Should_rejectMetaSuffixedKeys_When_valueIsNotValidJson() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    existingDepartment();

    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(
            () ->
                service.publishDepartmentDataPrivacy(
                    7L, 42L, Map.of("de__meta", "not-json{"), true));
    verify(agencyTopicRepository, never()).save(any());
  }

  @Test
  void publish_Should_rejectMetaSuffixedKeys_When_valueIsEmptyString() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    existingDepartment();

    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(
            () -> service.publishDepartmentDataPrivacy(7L, 42L, Map.of("de__meta", ""), true));
    verify(agencyTopicRepository, never()).save(any());
  }

  @Test
  void publish_Should_rejectMetaSuffixedKeys_When_valueIsJsonArray() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    existingDepartment();

    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(
            () ->
                service.publishDepartmentDataPrivacy(
                    7L, 42L, Map.of("de__meta", "[\"de\",\"en\"]"), true));
    verify(agencyTopicRepository, never()).save(any());
  }

  @Test
  void publish_Should_rejectMetaSuffixedKeys_When_valueIsJsonScalar() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    existingDepartment();

    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(
            () ->
                service.publishDepartmentDataPrivacy(
                    7L, 42L, Map.of("de__meta", "\"just-a-string\""), true));
    verify(agencyTopicRepository, never()).save(any());
  }

  @Test
  void publish_Should_rejectMetaSuffixedKeys_When_valueHasUnknownField() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    existingDepartment();

    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(
            () ->
                service.publishDepartmentDataPrivacy(
                    7L, 42L, Map.of("de__meta", "{\"mt\":true,\"unexpected\":\"x\"}"), true));
    verify(agencyTopicRepository, never()).save(any());
  }

  @Test
  void publish_Should_rejectMetaSuffixedKeys_When_mtIsNotBoolean() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    existingDepartment();

    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(
            () ->
                service.publishDepartmentDataPrivacy(
                    7L, 42L, Map.of("de__meta", "{\"mt\":\"true\"}"), true));
    verify(agencyTopicRepository, never()).save(any());
  }

  @Test
  void publish_Should_rejectMetaSuffixedKeys_When_srcIsBlank() {
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    existingDepartment();

    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(
            () ->
                service.publishDepartmentDataPrivacy(
                    7L, 42L, Map.of("de__meta", "{\"src\":\"   \"}"), true));
    verify(agencyTopicRepository, never()).save(any());
  }
}
