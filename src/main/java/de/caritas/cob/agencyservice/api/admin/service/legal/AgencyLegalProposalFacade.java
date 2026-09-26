package de.caritas.cob.agencyservice.api.admin.service.legal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.caritas.cob.agencyservice.api.admin.service.legal.AgencyLegalProposalService.DistributionCommand;
import de.caritas.cob.agencyservice.api.exception.httpresponses.AgencyAccessDeniedException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.InternalServerErrorException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.model.AgencyLegalDepartmentImpactDTO;
import de.caritas.cob.agencyservice.api.model.AgencyLegalDraftArchiveDTO;
import de.caritas.cob.agencyservice.api.model.AgencyLegalDraftDTO;
import de.caritas.cob.agencyservice.api.model.AgencyLegalProposalAdoptRequestDTO;
import de.caritas.cob.agencyservice.api.model.AgencyLegalProposalAdoptionDTO;
import de.caritas.cob.agencyservice.api.model.AgencyLegalProposalDTO;
import de.caritas.cob.agencyservice.api.model.AgencyLegalProposalDismissRequestDTO;
import de.caritas.cob.agencyservice.api.model.AgencyLegalProposalDistributionDTO;
import de.caritas.cob.agencyservice.api.model.AgencyLegalProposalDistributionRequestDTO;
import de.caritas.cob.agencyservice.api.model.AgencyLegalTemplateVersionDTO;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalDraft;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalDraftArchive;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposal;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalAdoptionMode;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalAudience;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalDistribution;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalSource;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.service.legal.DepartmentLegalPublicationState;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Authorisation and wire mapping for the Träger → Beratungsstelle templates. Forwarding is the
 * Träger admin's act (tenant-admin role, own Träger only); the inbox belongs to whoever may
 * administer that Beratungsstelle, checked with the same guard as the agency drafts.
 */
@Service
@RequiredArgsConstructor
public class AgencyLegalProposalFacade {

  private static final DateTimeFormatter WIRE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
  private static final TypeReference<LinkedHashMap<String, String>> LANGUAGE_MAP =
      new TypeReference<>() {};

  private final @NonNull AuthenticatedUser authenticatedUser;
  private final @NonNull LegalAdminAccessGuard accessGuard;
  private final @NonNull AgencyRepository agencyRepository;
  private final @NonNull AgencyLegalProposalService proposalService;
  private final @NonNull TraegerLegalTextClient traegerLegalTextClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public record DistributionOutcome(AgencyLegalProposalDistributionDTO body, boolean created) {}

  public DistributionOutcome distribute(AgencyLegalProposalDistributionRequestDTO request) {
    Long tenantId = traegerTenant(request.getTenantId());
    var kind = kind(request.getKind() == null ? null : request.getKind().getValue());
    var source =
        request.getSource() == null
            ? AgencyLegalProposalSource.DRAFT
            : AgencyLegalProposalSource.valueOf(request.getSource().getValue());
    var command =
        new DistributionCommand(
            request.getRequestKey(),
            tenantId,
            kind,
            source,
            request.getSourceRevision(),
            request.getAudience() == null
                ? null
                : AgencyLegalProposalAudience.valueOf(request.getAudience().getValue()),
            request.getAgencyIds() == null ? null : new LinkedHashSet<>(request.getAgencyIds()),
            authenticatedUser.getUserId());
    var retry = proposalService.findRetry(command);
    if (retry.isPresent()) {
      return new DistributionOutcome(distributionDto(retry.get()), false);
    }
    proposalService.checkRecipients(command);
    // Read before the write transaction: a remote call must not hold the proposal locks.
    var text =
        source == AgencyLegalProposalSource.DRAFT
            ? traegerLegalTextClient.draft(tenantId, kind)
            : traegerLegalTextClient.published(tenantId, kind);
    return new DistributionOutcome(distributionDto(proposalService.deliver(command, text)), true);
  }

  public List<AgencyLegalTemplateVersionDTO> templateHistory(String kind, Long tenantId) {
    Long traeger = traegerTenant(tenantId);
    return proposalService.sentHistory(traeger, kind(kind)).stream()
        .map(this::templateDto)
        .toList();
  }

  public List<AgencyLegalProposalDTO> list(Long agencyId, String kind) {
    var agency = authoriseAgency(agencyId);
    return proposalService.list(agencyId, kind == null ? null : kind(kind)).stream()
        .map(proposal -> proposalDto(proposal, agency))
        .toList();
  }

  public AgencyLegalProposalDTO get(Long agencyId, Long proposalId) {
    var agency = authoriseAgency(agencyId);
    return proposalDto(proposalService.get(agencyId, proposalId), agency);
  }

  public AgencyLegalProposalDTO dismiss(
      Long agencyId, Long proposalId, AgencyLegalProposalDismissRequestDTO request) {
    var agency = authoriseAgency(agencyId);
    return proposalDto(
        proposalService.dismiss(
            agencyId,
            proposalId,
            request == null ? null : request.getExpectedProposalRevision(),
            authenticatedUser.getUserId()),
        agency);
  }

  public AgencyLegalProposalAdoptionDTO adopt(
      Long agencyId, Long proposalId, AgencyLegalProposalAdoptRequestDTO request) {
    var agency = authoriseAgency(agencyId);
    if (request == null || request.getMode() == null) {
      throw new BadRequestException("Adoption mode is required");
    }
    var result =
        proposalService.adopt(
            agencyId,
            proposalId,
            AgencyLegalProposalAdoptionMode.valueOf(request.getMode().getValue()),
            request.getExpectedProposalRevision(),
            request.getExpectedDraftRevision(),
            authenticatedUser.getUserId());
    var dto =
        new AgencyLegalProposalAdoptionDTO()
            .draft(draftDto(result.draft()))
            .proposal(proposalDto(result.proposal(), agency))
            .departmentImpact(impact(agency, result.proposal().getKind()));
    result.archivedDraft().map(this::archiveDto).ifPresent(dto::setArchivedDraft);
    return dto;
  }

  public List<AgencyLegalDraftArchiveDTO> archives(Long agencyId, String kind) {
    authoriseAgency(agencyId);
    return proposalService.archives(agencyId, kind == null ? null : kind(kind)).stream()
        .map(this::archiveDto)
        .toList();
  }

  public AgencyLegalDraftArchiveDTO archive(Long agencyId, Long archiveId) {
    authoriseAgency(agencyId);
    return archiveDto(proposalService.archive(agencyId, archiveId));
  }

  /**
   * The Träger the caller acts for. A platform admin (tenant 0) must name it; a Träger admin may
   * only name its own. Restricted and plain agency admins are not Träger admins.
   */
  private Long traegerTenant(Long requested) {
    if (!authenticatedUser.isTenantSuperAdmin()
        || authenticatedUser.hasRestrictedAgencyPriviliges()) {
      throw new AgencyAccessDeniedException();
    }
    Long caller = accessGuard.resolveEffectiveTenantId();
    if (caller == null || caller == 0L) {
      if (requested == null || requested <= 0) {
        throw new BadRequestException("tenantId is required when acting for a Träger");
      }
      return requested;
    }
    if (requested != null && !requested.equals(caller)) {
      throw new AgencyAccessDeniedException();
    }
    return caller;
  }

  /** Ownership and tenant are checked before any proposal lookup, so 404 cannot probe. */
  private Agency authoriseAgency(Long agencyId) {
    accessGuard.assertRestrictedAdminOwnsAgency(agencyId);
    var agency =
        agencyRepository.findByIdIn(List.of(agencyId)).stream()
            .findFirst()
            .orElseThrow(NotFoundException::new);
    accessGuard.assertCallerTenantMatches(agency);
    return agency;
  }

  private AgencyLegalDepartmentImpactDTO impact(Agency agency, LegalTextKind kind) {
    Collection<AgencyTopic> topics =
        agency.getAgencyTopics() == null ? List.of() : agency.getAgencyTopics();
    Predicate<AgencyTopic> ownText =
        kind == LegalTextKind.DPP
            ? DepartmentLegalPublicationState::hasPublishedDpp
            : DepartmentLegalPublicationState::hasPublishedImprint;
    var forked = topics.stream().filter(ownText).map(AgencyTopic::getTopicId).sorted().toList();
    return new AgencyLegalDepartmentImpactDTO()
        .affected(topics.size() - forked.size())
        .notAffected(forked.size())
        .notAffectedTopicIds(new ArrayList<>(forked));
  }

  private AgencyLegalProposalDistributionDTO distributionDto(
      AgencyLegalProposalService.DeliveryResult result) {
    var distribution = result.distribution();
    return new AgencyLegalProposalDistributionDTO()
        .distributionId(distribution.getId())
        .requestKey(distribution.getRequestKey())
        .kind(AgencyLegalProposalDistributionDTO.KindEnum.fromValue(distribution.getKind().name()))
        .source(
            AgencyLegalProposalDistributionDTO.SourceEnum.fromValue(
                distribution.getSource().name()))
        .sourceRevision(distribution.getSourceRevision())
        .audience(
            AgencyLegalProposalDistributionDTO.AudienceEnum.fromValue(
                distribution.getAudience().name()))
        .recipientAgencyIds(
            new ArrayList<>(AgencyLegalProposalService.recipients(distribution)))
        .createdAt(format(distribution.getCreatedAt()))
        .proposals(result.proposals().stream().map(p -> proposalDto(p, null)).toList());
  }

  private AgencyLegalTemplateVersionDTO templateDto(AgencyLegalProposalDistribution distribution) {
    var recipients = AgencyLegalProposalService.recipients(distribution);
    var dto =
        new AgencyLegalTemplateVersionDTO()
            .distributionId(distribution.getId())
            .kind(AgencyLegalTemplateVersionDTO.KindEnum.fromValue(distribution.getKind().name()))
            .source(
                AgencyLegalTemplateVersionDTO.SourceEnum.fromValue(
                    distribution.getSource().name()))
            .sourceRevision(distribution.getSourceRevision())
            .audience(
                AgencyLegalTemplateVersionDTO.AudienceEnum.fromValue(
                    distribution.getAudience().name()))
            .recipientAgencyIds(new ArrayList<>(recipients))
            .recipientCount(recipients.size())
            .content(read(distribution.getContent()))
            .createdBy(distribution.getCreatedBy())
            .createdAt(format(distribution.getCreatedAt()));
    if (distribution.getConsentText() != null) {
      dto.setConsentText(read(distribution.getConsentText()));
    }
    return dto;
  }

  /** The Fachbereich impact needs the recipient agency's topics; null leaves it out. */
  private AgencyLegalProposalDTO proposalDto(AgencyLegalProposal proposal, Agency agency) {
    var dto =
        new AgencyLegalProposalDTO()
            .id(proposal.getId())
            .recipientAgencyId(proposal.getRecipientAgencyId())
            .kind(AgencyLegalProposalDTO.KindEnum.fromValue(proposal.getKind().name()))
            .content(read(proposal.getContent()))
            .consentText(read(proposal.getConsentText()))
            .status(AgencyLegalProposalDTO.StatusEnum.fromValue(proposal.getStatus().name()))
            .revision(AgencyLegalProposalService.revision(proposal))
            .source(AgencyLegalProposalDTO.SourceEnum.fromValue(proposal.getSource().name()))
            .sourceRevision(proposal.getSourceRevision())
            .distributionId(proposal.getDistributionId())
            .audience(AgencyLegalProposalDTO.AudienceEnum.fromValue(proposal.getAudience().name()))
            .createdBy(proposal.getCreatedBy())
            .createdAt(format(proposal.getCreatedAt()))
            .decidedBy(proposal.getDecidedBy())
            .decidedAt(format(proposal.getDecidedAt()))
            .supersededByProposalId(proposal.getSupersededByProposalId())
            .supersededAt(format(proposal.getSupersededAt()));
    if (agency != null) {
      dto.setDepartmentImpact(impact(agency, proposal.getKind()));
    }
    return dto;
  }

  private AgencyLegalDraftDTO draftDto(AgencyLegalDraft draft) {
    return new AgencyLegalDraftDTO()
        .kind(AgencyLegalDraftDTO.KindEnum.fromValue(draft.getKind().name()))
        .content(read(draft.getContent()))
        .consentText(read(draft.getConsentText()))
        .revision(AgencyLegalProposalService.draftRevision(draft))
        .savedAt(format(draft.getSavedAt()))
        .originProposalId(draft.getOriginProposalId());
  }

  private AgencyLegalDraftArchiveDTO archiveDto(AgencyLegalDraftArchive archive) {
    return new AgencyLegalDraftArchiveDTO()
        .id(archive.getId())
        .agencyId(archive.getAgencyId())
        .kind(AgencyLegalDraftArchiveDTO.KindEnum.fromValue(archive.getKind().name()))
        .draftRevision(archive.getDraftRevision())
        .content(read(archive.getContent()))
        .consentText(read(archive.getConsentText()))
        .draftSavedAt(format(archive.getDraftSavedAt()))
        .originProposalId(archive.getOriginProposalId())
        .replacedByProposalId(archive.getReplacedByProposalId())
        .archivedBy(archive.getArchivedBy())
        .archivedAt(format(archive.getArchivedAt()));
  }

  private Map<String, String> read(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, LANGUAGE_MAP);
    } catch (JsonProcessingException e) {
      throw new InternalServerErrorException("Could not read a stored legal template", e);
    }
  }

  private static String format(LocalDateTime value) {
    return value == null ? null : WIRE.format(value);
  }

  /** PRIVACY is TenantService's spelling of DPP; the Admin's shared kind type sends either. */
  private static LegalTextKind kind(String value) {
    if (value == null) {
      throw new BadRequestException("Legal text kind is required");
    }
    return switch (value) {
      case "DPP", "PRIVACY" -> LegalTextKind.DPP;
      case "IMPRINT" -> LegalTextKind.IMPRINT;
      default -> throw new BadRequestException("Unsupported legal text kind");
    };
  }
}
