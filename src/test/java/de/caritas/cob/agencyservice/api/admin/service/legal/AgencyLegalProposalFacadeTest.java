package de.caritas.cob.agencyservice.api.admin.service.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.admin.service.legal.AgencyLegalProposalService.DeliveryResult;
import de.caritas.cob.agencyservice.api.admin.service.legal.TraegerLegalTextClient.TraegerLegalText;
import de.caritas.cob.agencyservice.api.exception.httpresponses.AgencyAccessDeniedException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.model.AgencyLegalProposalDistributionRequestDTO;
import de.caritas.cob.agencyservice.api.model.AgencyLegalProposalDistributionRequestDTO.AudienceEnum;
import de.caritas.cob.agencyservice.api.model.AgencyLegalProposalDistributionRequestDTO.KindEnum;
import de.caritas.cob.agencyservice.api.model.AgencyLegalProposalDistributionRequestDTO.SourceEnum;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalAudience;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalDistribution;
import de.caritas.cob.agencyservice.api.repository.legaltext.AgencyLegalProposalSource;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgencyLegalProposalFacadeTest {

  @Mock private LegalAdminAccessGuard accessGuard;
  @Mock private AgencyRepository agencyRepository;
  @Mock private AgencyLegalProposalService proposalService;
  @Mock private TraegerLegalTextClient traegerLegalTextClient;

  private final AuthenticatedUser user = new AuthenticatedUser();
  private AgencyLegalProposalFacade facade;

  @BeforeEach
  void setUp() {
    facade =
        new AgencyLegalProposalFacade(
            user, accessGuard, agencyRepository, proposalService, traegerLegalTextClient);
    user.setUserId("admin");
  }

  @Test
  void onlyATraegerAdminMayForward() {
    user.setRoles(Set.of("agency-admin"));

    assertThatThrownBy(() -> facade.distribute(request(null)))
        .isInstanceOf(AgencyAccessDeniedException.class);
    verifyNoInteractions(proposalService, traegerLegalTextClient);
  }

  @Test
  void aPlatformAdminMustNameTheTraeger() {
    user.setRoles(Set.of("tenant-admin", "agency-admin"));
    when(accessGuard.resolveEffectiveTenantId()).thenReturn(0L);

    assertThatThrownBy(() -> facade.distribute(request(null)))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void publishedSourceReadsThePublishedTraegerTextForTheOwnTraeger() {
    user.setRoles(Set.of("tenant-admin", "agency-admin"));
    when(accessGuard.resolveEffectiveTenantId()).thenReturn(4L);
    var text = new TraegerLegalText("version:9", Map.of("de", "<p>live</p>"), null);
    when(traegerLegalTextClient.published(4L, LegalTextKind.IMPRINT)).thenReturn(text);
    var distribution =
        AgencyLegalProposalDistribution.builder()
            .id("d")
            .requestKey("k")
            .tenantId(4L)
            .kind(LegalTextKind.IMPRINT)
            .source(AgencyLegalProposalSource.PUBLISHED)
            .sourceRevision("version:9")
            .audience(AgencyLegalProposalAudience.SELECTED)
            .recipientIds("7")
            .createdAt(LocalDateTime.now())
            .build();
    when(proposalService.deliver(any(), any()))
        .thenReturn(new DeliveryResult(distribution, List.of(), true));

    var outcome =
        facade.distribute(
            request(4L).kind(KindEnum.IMPRINT).source(SourceEnum.PUBLISHED).sourceRevision("version:9"));

    assertThat(outcome.created()).isTrue();
    assertThat(outcome.body().getRecipientAgencyIds()).containsExactly(7L);
    verify(proposalService).deliver(any(), org.mockito.ArgumentMatchers.eq(text));
  }

  private AgencyLegalProposalDistributionRequestDTO request(Long tenantId) {
    return new AgencyLegalProposalDistributionRequestDTO()
        .requestKey("k")
        .kind(KindEnum.PRIVACY)
        .sourceRevision("12:3")
        .audience(AudienceEnum.SELECTED)
        .agencyIds(List.of(7L))
        .tenantId(tenantId);
  }
}
