package de.caritas.cob.agencyservice.api.admin.service.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.admin.service.legal.AgencyLegalProposalService.DistributionCommand;
import de.caritas.cob.agencyservice.api.admin.service.legal.TraegerLegalTextClient.TraegerLegalText;
import de.caritas.cob.agencyservice.api.exception.httpresponses.AgencyAccessDeniedException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.ConflictException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalDraft;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalDraftArchiveRepository;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalDraftRepository;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposal;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalAdoptionMode;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalAudience;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalDistribution;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalDistributionRepository;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalRepository;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalSource;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalStatus;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgencyLegalProposalServiceTest {

  @Mock private AgencyLegalProposalDistributionRepository distributionRepository;
  @Mock private AgencyLegalProposalRepository proposalRepository;
  @Mock private AgencyLegalDraftRepository draftRepository;
  @Mock private AgencyLegalDraftArchiveRepository archiveRepository;
  @Mock private AgencyRepository agencyRepository;
  @Mock private LegalContentSanitizer contentSanitizer;
  @Mock private ConsentTextService consentTextService;

  private AgencyLegalProposalService service;
  private final TraegerLegalText source =
      new TraegerLegalText("12:3", Map.of("de", "<p>Träger</p>"), Map.of("de", "Zustimmung"));

  @BeforeEach
  void setUp() {
    service =
        new AgencyLegalProposalService(
            distributionRepository,
            proposalRepository,
            draftRepository,
            archiveRepository,
            agencyRepository,
            contentSanitizer,
            consentTextService);
    lenient().when(contentSanitizer.sanitizeToJson(any())).thenReturn("{\"de\":\"<p>Träger</p>\"}");
    lenient()
        .when(consentTextService.sanitizeAndValidate(any(), org.mockito.ArgumentMatchers.eq(false)))
        .thenReturn("{\"de\":\"Zustimmung\"}");
    lenient().when(distributionRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    lenient()
        .when(proposalRepository.saveAllAndFlush(any()))
        .thenAnswer(
            i -> {
              List<AgencyLegalProposal> saved = i.getArgument(0);
              long id = 100;
              for (var p : saved) {
                p.setId(id++);
                p.setVersion(0L);
              }
              return saved;
            });
  }

  @Test
  void allForwardsToEveryCurrentBeratungsstelleOfTheTraegerAndSupersedesOlderOffersLast() {
    when(agencyRepository.findByTenantIdAndDeleteDateNullOrderByIdAsc(1L))
        .thenReturn(List.of(agency(7L, 1L), agency(3L, 1L)));
    var older = proposal(50L, 3L, AgencyLegalProposalStatus.DISMISSED);
    when(proposalRepository.findLockedOpenByRecipients(any(), any(), any()))
        .thenReturn(List.of(older));

    var result = service.deliver(command("key", AgencyLegalProposalAudience.ALL, Set.of()), source);

    assertThat(result.created()).isTrue();
    assertThat(result.distribution().getRecipientIds()).isEqualTo("3,7");
    assertThat(result.proposals())
        .extracting(AgencyLegalProposal::getRecipientAgencyId)
        .containsExactly(3L, 7L);
    assertThat(result.proposals())
        .allSatisfy(
            p -> {
              assertThat(p.getStatus()).isEqualTo(AgencyLegalProposalStatus.PENDING);
              assertThat(p.getContent()).isEqualTo("{\"de\":\"<p>Träger</p>\"}");
              assertThat(p.getConsentText()).isEqualTo("{\"de\":\"Zustimmung\"}");
              assertThat(p.getSourceRevision()).isEqualTo("12:3");
            });
    assertThat(older.getStatus()).isEqualTo(AgencyLegalProposalStatus.SUPERSEDED);
    assertThat(older.getSupersededByProposalId()).isEqualTo(100L);
    var order = inOrder(proposalRepository);
    order.verify(proposalRepository).saveAllAndFlush(any());
    order.verify(proposalRepository).findLockedOpenByRecipients(any(), any(), any());
  }

  @Test
  void anImprintNeverCarriesAConsentSentence() {
    when(agencyRepository.findAllById(Set.of(7L))).thenReturn(List.of(agency(7L, 1L)));
    var command =
        new DistributionCommand(
            "key",
            1L,
            LegalTextKind.IMPRINT,
            AgencyLegalProposalSource.DRAFT,
            "12:3",
            AgencyLegalProposalAudience.SELECTED,
            Set.of(7L),
            "traeger-admin");

    var result = service.deliver(command, source);

    assertThat(result.proposals().get(0).getConsentText()).isNull();
  }

  @Test
  void recipientsAreValidated() {
    assertThatThrownBy(
            () -> service.checkRecipients(command("k", AgencyLegalProposalAudience.ALL, Set.of(7L))))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(
            () -> service.checkRecipients(command("k", AgencyLegalProposalAudience.SELECTED, Set.of())))
        .isInstanceOf(BadRequestException.class);
    when(agencyRepository.findByTenantIdAndDeleteDateNullOrderByIdAsc(1L)).thenReturn(List.of());
    assertThatThrownBy(
            () -> service.checkRecipients(command("k", AgencyLegalProposalAudience.ALL, Set.of())))
        .isInstanceOf(BadRequestException.class);
    when(agencyRepository.findAllById(Set.of(7L, 8L)))
        .thenReturn(List.of(agency(7L, 1L), agency(8L, 2L)));
    assertThatThrownBy(
            () ->
                service.checkRecipients(
                    command("k", AgencyLegalProposalAudience.SELECTED, Set.of(7L, 8L))))
        .isInstanceOf(AgencyAccessDeniedException.class);
    when(agencyRepository.findAllById(Set.of(9L))).thenReturn(List.of());
    assertThatThrownBy(
            () ->
                service.checkRecipients(command("k", AgencyLegalProposalAudience.SELECTED, Set.of(9L))))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void aStaleSourceRevisionSendsNothing() {
    var stale = new TraegerLegalText("12:4", source.content(), source.consentText());

    assertThatThrownBy(
            () ->
                service.deliver(
                    command("key", AgencyLegalProposalAudience.SELECTED, Set.of(7L)), stale))
        .isInstanceOf(ConflictException.class);
    verify(distributionRepository, never()).saveAndFlush(any());
  }

  @Test
  void aReusedRequestKeyWithDifferentInputConflicts() {
    when(agencyRepository.findAllById(Set.of(7L))).thenReturn(List.of(agency(7L, 1L)));
    var first =
        service.deliver(command("key", AgencyLegalProposalAudience.SELECTED, Set.of(7L)), source);
    when(distributionRepository.findByRequestKey("key"))
        .thenReturn(Optional.of(first.distribution()));

    assertThat(
            service.findRetry(command("key", AgencyLegalProposalAudience.SELECTED, Set.of(7L))))
        .hasValueSatisfying(retry -> assertThat(retry.created()).isFalse());
    assertThatThrownBy(
            () ->
                service.findRetry(command("key", AgencyLegalProposalAudience.SELECTED, Set.of(8L))))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void adoptIntoAnEmptyDraftCopiesTheOfferAndPublishesNothing() {
    var offer = proposal(5L, 7L, AgencyLegalProposalStatus.PENDING);
    when(proposalRepository.findLockedByIdAndRecipientAgencyId(5L, 7L))
        .thenReturn(Optional.of(offer));
    when(draftRepository.findLockedByAgencyIdAndKind(7L, LegalTextKind.DPP))
        .thenReturn(Optional.empty());
    when(draftRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    when(proposalRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

    var result =
        service.adopt(
            7L, 5L, AgencyLegalProposalAdoptionMode.CREATE_IF_EMPTY, "5:0", null, "admin");

    assertThat(result.draft().getContent()).isEqualTo(offer.getContent());
    assertThat(result.draft().getConsentText()).isEqualTo(offer.getConsentText());
    assertThat(result.draft().getOriginProposalId()).isEqualTo(5L);
    assertThat(result.proposal().getStatus()).isEqualTo(AgencyLegalProposalStatus.ADOPTED);
    assertThat(result.archivedDraft()).isEmpty();
    verify(agencyRepository, never()).save(any());
  }

  @Test
  void adoptingASupersededOfferOrAStaleProposalRevisionConflicts() {
    var superseded = proposal(5L, 7L, AgencyLegalProposalStatus.SUPERSEDED);
    when(proposalRepository.findLockedByIdAndRecipientAgencyId(5L, 7L))
        .thenReturn(Optional.of(superseded));
    assertThatThrownBy(
            () ->
                service.adopt(
                    7L, 5L, AgencyLegalProposalAdoptionMode.CREATE_IF_EMPTY, "5:0", null, "a"))
        .isInstanceOf(ConflictException.class);

    superseded.setStatus(AgencyLegalProposalStatus.PENDING);
    assertThatThrownBy(
            () ->
                service.adopt(
                    7L, 5L, AgencyLegalProposalAdoptionMode.CREATE_IF_EMPTY, "5:9", null, "a"))
        .isInstanceOf(ConflictException.class);
    verify(draftRepository, never()).saveAndFlush(any());
  }

  @Test
  void archiveAndReplaceNeedsTheCurrentDraftRevision() {
    var offer = proposal(5L, 7L, AgencyLegalProposalStatus.DISMISSED);
    when(proposalRepository.findLockedByIdAndRecipientAgencyId(5L, 7L))
        .thenReturn(Optional.of(offer));
    var current =
        AgencyLegalDraft.builder()
            .rowId("row")
            .version(2L)
            .agencyId(7L)
            .kind(LegalTextKind.DPP)
            .content("{\"de\":\"eigene\"}")
            .savedAt(LocalDateTime.now())
            .build();
    when(draftRepository.findLockedByAgencyIdAndKind(7L, LegalTextKind.DPP))
        .thenReturn(Optional.of(current));

    assertThatThrownBy(
            () ->
                service.adopt(
                    7L, 5L, AgencyLegalProposalAdoptionMode.ARCHIVE_AND_REPLACE, "5:0", null, "a"))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(
            () ->
                service.adopt(
                    7L, 5L, AgencyLegalProposalAdoptionMode.ARCHIVE_AND_REPLACE, "5:0", "row:1", "a"))
        .isInstanceOf(ConflictException.class);
    verify(archiveRepository, never()).saveAndFlush(any());

    when(archiveRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    when(draftRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    when(proposalRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    var result =
        service.adopt(
            7L, 5L, AgencyLegalProposalAdoptionMode.ARCHIVE_AND_REPLACE, "5:0", "row:2", "a");

    assertThat(result.archivedDraft())
        .hasValueSatisfying(
            archive -> {
              assertThat(archive.getContent()).isEqualTo("{\"de\":\"eigene\"}");
              assertThat(archive.getDraftRevision()).isEqualTo("row:2");
              assertThat(archive.getReplacedByProposalId()).isEqualTo(5L);
            });
    assertThat(result.draft().getContent()).isEqualTo(offer.getContent());
  }

  @Test
  void dismissOnlyAPendingOffer() {
    var dismissed = proposal(5L, 7L, AgencyLegalProposalStatus.DISMISSED);
    when(proposalRepository.findLockedByIdAndRecipientAgencyId(5L, 7L))
        .thenReturn(Optional.of(dismissed));

    assertThatThrownBy(() -> service.dismiss(7L, 5L, "5:0", "a"))
        .isInstanceOf(ConflictException.class);
  }

  private DistributionCommand command(
      String key, AgencyLegalProposalAudience audience, Set<Long> agencyIds) {
    return new DistributionCommand(
        key,
        1L,
        LegalTextKind.DPP,
        AgencyLegalProposalSource.DRAFT,
        "12:3",
        audience,
        agencyIds,
        "traeger-admin");
  }

  private Agency agency(Long id, Long tenantId) {
    return Agency.builder().id(id).tenantId(tenantId).name("A" + id).consultingTypeId(1).build();
  }

  private AgencyLegalProposal proposal(Long id, Long agencyId, AgencyLegalProposalStatus status) {
    return AgencyLegalProposal.builder()
        .id(id)
        .version(0L)
        .recipientAgencyId(agencyId)
        .tenantId(1L)
        .kind(LegalTextKind.DPP)
        .distributionId("d")
        .audience(AgencyLegalProposalAudience.SELECTED)
        .source(AgencyLegalProposalSource.DRAFT)
        .sourceRevision("12:2")
        .content("{\"de\":\"<p>Angebot</p>\"}")
        .consentText("{\"de\":\"Zustimmung\"}")
        .status(status)
        .createdAt(LocalDateTime.now())
        .build();
  }
}
