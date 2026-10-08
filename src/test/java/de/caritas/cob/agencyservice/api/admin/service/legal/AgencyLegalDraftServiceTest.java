package de.caritas.cob.agencyservice.api.admin.service.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.exception.httpresponses.AgencyAccessDeniedException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.ConflictException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalDraft;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalDraftRepository;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgencyLegalDraftServiceTest {

  @Mock private AgencyLegalDraftRepository draftRepository;
  @Mock private AgencyRepository agencyRepository;
  @Mock private LegalAdminAccessGuard accessGuard;
  @Mock private LegalContentSanitizer contentSanitizer;
  @Mock private ConsentTextService consentTextService;

  private AgencyLegalDraftService service;
  private Agency agency;

  @BeforeEach
  void setUp() {
    service =
        new AgencyLegalDraftService(
            draftRepository, agencyRepository, accessGuard, contentSanitizer, consentTextService);
    agency =
        Agency.builder().id(7L).tenantId(3L).name("Zentrum").consultingTypeId(1).build();
    lenient().when(agencyRepository.findById(7L)).thenReturn(Optional.of(agency));
  }

  @Test
  void saveAndReloadDpp_Should_roundTripLanguageMapsAndConsent_withoutPublishing() {
    agency.setContentDpp("{\"de\":\"<p>veröffentlicht</p>\"}");
    agency.setConsentText("{\"de\":\"veröffentlichte Zustimmung\"}");
    var content = Map.of("de", "<p>Entwurf</p>", "en", "<p>Draft</p>");
    var consent = Map.of("de", "Ich stimme dem Entwurf zu");
    when(contentSanitizer.sanitizeToJson(content))
        .thenReturn("{\"de\":\"<p>Entwurf</p>\",\"en\":\"<p>Draft</p>\"}");
    when(consentTextService.sanitizeAndValidate(consent, false))
        .thenReturn("{\"de\":\"Ich stimme dem Entwurf zu\"}");
    when(draftRepository.findByAgencyIdAndKind(7L, LegalTextKind.DPP))
        .thenReturn(Optional.empty());
    when(draftRepository.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              AgencyLegalDraft stored = invocation.getArgument(0);
              stored.setVersion(0L);
              return stored;
            });

    var saved = service.save(7L, LegalTextKind.DPP, content, consent, null);

    assertThat(saved.content()).containsEntry("de", "<p>Entwurf</p>");
    assertThat(saved.content()).containsEntry("en", "<p>Draft</p>");
    assertThat(saved.consentText()).containsEntry("de", "Ich stimme dem Entwurf zu");
    assertThat(saved.revision()).matches("[0-9a-f-]{36}:0");
    assertThat(agency.getContentDpp()).isEqualTo("{\"de\":\"<p>veröffentlicht</p>\"}");
    assertThat(agency.getConsentText())
        .isEqualTo("{\"de\":\"veröffentlichte Zustimmung\"}");

    var entity = ArgumentCaptor.forClass(AgencyLegalDraft.class);
    verify(draftRepository).saveAndFlush(entity.capture());
    assertThat(entity.getValue().getAgencyId()).isEqualTo(7L);
    assertThat(entity.getValue().getKind()).isEqualTo(LegalTextKind.DPP);
  }

  @Test
  void get_Should_authoriseAgencyBeforeLookingUpWhetherDraftExists() {
    InOrder order = inOrder(accessGuard, agencyRepository, draftRepository);
    when(draftRepository.findByAgencyIdAndKind(7L, LegalTextKind.IMPRINT))
        .thenReturn(Optional.empty());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.get(7L, LegalTextKind.IMPRINT));

    order.verify(accessGuard).assertRestrictedAdminOwnsAgency(7L);
    order.verify(agencyRepository).findById(7L);
    order.verify(accessGuard).assertCallerTenantMatches(agency);
    order.verify(draftRepository).findByAgencyIdAndKind(7L, LegalTextKind.IMPRINT);
  }

  @Test
  void get_Should_notRevealDraftExistenceWhenAgencyAccessIsDenied() {
    org.mockito.Mockito.doThrow(new AgencyAccessDeniedException())
        .when(accessGuard)
        .assertRestrictedAdminOwnsAgency(9L);

    assertThatExceptionOfType(AgencyAccessDeniedException.class)
        .isThrownBy(() -> service.get(9L, LegalTextKind.DPP));

    verify(agencyRepository, never()).findById(any());
    verify(draftRepository, never()).findByAgencyIdAndKind(any(), any());
  }

  @Test
  void saveImprint_Should_rejectConsentWording() {
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(
            () ->
                service.save(
                    7L,
                    LegalTextKind.IMPRINT,
                    Map.of("de", "Impressum"),
                    Map.of("de", "nicht erlaubt"),
                    null));

    verify(draftRepository, never()).saveAndFlush(any());
  }

  @Test
  void update_Should_useWholeOpaqueRevisionAsCompareAndSwapToken() {
    var rowId = "8c65e53a-1e5d-4e72-80da-13323900448c";
    when(contentSanitizer.sanitizeToJson(any())).thenReturn("{\"de\":\"neu\"}");
    when(consentTextService.sanitizeAndValidate(any(), org.mockito.ArgumentMatchers.eq(false)))
        .thenReturn(null);
    when(draftRepository.compareAndSwap(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq(LegalTextKind.DPP),
            org.mockito.ArgumentMatchers.eq(rowId),
            org.mockito.ArgumentMatchers.eq(4L),
            org.mockito.ArgumentMatchers.eq("{\"de\":\"neu\"}"),
            org.mockito.ArgumentMatchers.isNull(),
            any(LocalDateTime.class)))
        .thenReturn(1);
    when(draftRepository.findByAgencyIdAndKind(7L, LegalTextKind.DPP))
        .thenReturn(
            Optional.of(
                AgencyLegalDraft.builder()
                    .rowId(rowId)
                    .agencyId(7L)
                    .kind(LegalTextKind.DPP)
                    .content("{\"de\":\"neu\"}")
                    .version(5L)
                    .savedAt(LocalDateTime.now())
                    .build()));

    var updated = service.save(7L, LegalTextKind.DPP, Map.of("de", "neu"), Map.of(), rowId + ":4");

    assertThat(updated.revision()).isEqualTo(rowId + ":5");
  }

  @Test
  void staleUpdate_Should_conflictWithoutChangingStoredContent() {
    when(contentSanitizer.sanitizeToJson(any())).thenReturn("{\"de\":\"stale\"}");
    when(consentTextService.sanitizeAndValidate(any(), org.mockito.ArgumentMatchers.eq(false)))
        .thenReturn(null);
    when(draftRepository.compareAndSwap(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(0);

    assertThatExceptionOfType(ConflictException.class)
        .isThrownBy(
            () ->
                service.save(
                    7L,
                    LegalTextKind.DPP,
                    Map.of("de", "stale"),
                    Map.of(),
                    "8c65e53a-1e5d-4e72-80da-13323900448c:4"));
  }

  @Test
  void deleteAfterRecreate_Should_rejectRevisionFromDeletedRow() {
    when(draftRepository.compareAndDelete(
            7L,
            LegalTextKind.DPP,
            "8c65e53a-1e5d-4e72-80da-13323900448c",
            2L))
        .thenReturn(0);

    assertThatExceptionOfType(ConflictException.class)
        .isThrownBy(
            () ->
                service.delete(
                    7L,
                    LegalTextKind.DPP,
                    "8c65e53a-1e5d-4e72-80da-13323900448c:2"));
  }
}
