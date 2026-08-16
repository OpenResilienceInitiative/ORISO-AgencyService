package de.caritas.cob.agencyservice.api.admin.service.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextLevel;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextVersion;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextVersionRepository;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** ADR-021 decision 3: what does and does not become a version. */
@ExtendWith(MockitoExtension.class)
class LegalTextVersionServiceTest {

  @Mock private LegalTextVersionRepository legalTextVersionRepository;
  @Mock private AuthenticatedUser authenticatedUser;

  @InjectMocks private LegalTextVersionService service;

  private void noOpenVersion() {
    when(legalTextVersionRepository.findByOwnerLevelAndOwnerIdAndKindAndSupersededAtIsNull(
            any(), any(), any()))
        .thenReturn(List.of());
  }

  private LegalTextVersion openVersion(String content) {
    return LegalTextVersion.builder()
        .id(1L)
        .ownerLevel(LegalTextLevel.AGENCY)
        .ownerId(7L)
        .kind(LegalTextKind.DPP)
        .content(content)
        .publishedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
        .build();
  }

  @Test
  void recordPublication_Should_storeTheWording_withPublisherAndTimestamp() {
    noOpenVersion();
    when(authenticatedUser.getUserId()).thenReturn("admin-uuid");
    when(legalTextVersionRepository.save(any(LegalTextVersion.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result =
        service.recordPublication(LegalTextLevel.DEPARTMENT, 42L, LegalTextKind.DPP, 3L, "{\"de\":\"<p>x</p>\"}", null);

    assertThat(result).isPresent();
    var stored = result.get();
    assertThat(stored.getOwnerLevel()).isEqualTo(LegalTextLevel.DEPARTMENT);
    assertThat(stored.getOwnerId()).isEqualTo(42L);
    assertThat(stored.getKind()).isEqualTo(LegalTextKind.DPP);
    assertThat(stored.getTenantId()).isEqualTo(3L);
    assertThat(stored.getContent()).isEqualTo("{\"de\":\"<p>x</p>\"}");
    assertThat(stored.getPublishedBy()).isEqualTo("admin-uuid");
    assertThat(stored.getPublishedAt()).isNotNull();
    assertThat(stored.getSupersededAt()).isNull();
  }

  @Test
  void recordPublication_Should_supersedeThePreviousCurrentVersion() {
    var previous = openVersion("old");
    when(legalTextVersionRepository.findByOwnerLevelAndOwnerIdAndKindAndSupersededAtIsNull(
            LegalTextLevel.AGENCY, 7L, LegalTextKind.DPP))
        .thenReturn(List.of(previous));
    when(legalTextVersionRepository.save(any(LegalTextVersion.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recordPublication(LegalTextLevel.AGENCY, 7L, LegalTextKind.DPP, 3L, "new", null);

    ArgumentCaptor<List<LegalTextVersion>> captor = ArgumentCaptor.captor();
    verify(legalTextVersionRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).singleElement().extracting(LegalTextVersion::getSupersededAt)
        .isNotNull();
    // The old wording itself is untouched — an archive that rewrites history proves nothing.
    assertThat(previous.getContent()).isEqualTo("old");
  }

  @Test
  void recordPublication_Should_skip_When_theWordingIsUnchanged() {
    when(legalTextVersionRepository.findByOwnerLevelAndOwnerIdAndKindAndSupersededAtIsNull(
            LegalTextLevel.AGENCY, 7L, LegalTextKind.DPP))
        .thenReturn(List.of(openVersion("same")));

    var result = service.recordPublication(LegalTextLevel.AGENCY, 7L, LegalTextKind.DPP, 3L, "same", null);

    // Load-bearing for the agency level, where every unrelated update resends the legal texts:
    // without this, editing the opening hours would forge a new version of the privacy policy.
    assertThat(result).isEmpty();
    verify(legalTextVersionRepository, never()).save(any());
    verify(legalTextVersionRepository, never()).saveAll(anyList());
  }

  @Test
  void recordPublication_Should_skip_When_thereIsNoDocument() {
    assertThat(service.recordPublication(LegalTextLevel.AGENCY, 7L, LegalTextKind.DPP, 3L, null, null))
        .isEmpty();
    assertThat(service.recordPublication(LegalTextLevel.AGENCY, 7L, LegalTextKind.DPP, 3L, "   ", null))
        .isEmpty();
    // The sanitizer serialises "no translations" to an empty JSON object, which is just as absent.
    assertThat(service.recordPublication(LegalTextLevel.AGENCY, 7L, LegalTextKind.DPP, 3L, "{}", null))
        .isEmpty();

    verify(legalTextVersionRepository, never()).save(any());
  }

  @Test
  void recordPublication_Should_recordAnUnknownPublisher_ratherThanGuessingOne() {
    noOpenVersion();
    when(authenticatedUser.getUserId()).thenReturn(null);
    when(legalTextVersionRepository.save(any(LegalTextVersion.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result =
        service.recordPublication(LegalTextLevel.AGENCY, 7L, LegalTextKind.IMPRINT, null, "x", null);

    assertThat(result).isPresent();
    assertThat(result.get().getPublishedBy()).isNull();
  }
}
