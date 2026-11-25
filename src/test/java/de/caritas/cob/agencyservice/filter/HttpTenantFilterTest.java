package de.caritas.cob.agencyservice.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.tenant.TenantResolverService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@ExtendWith(MockitoExtension.class)
class HttpTenantFilterTest {

  @InjectMocks HttpTenantFilter httpTenantFilter;

  @Mock private TenantResolverService tenantResolverService;


  @Mock HttpServletRequest request;

  @Mock HttpServletResponse response;

  @Mock FilterChain filterChain;

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @Test
  void doFilterInternal_Should_NotApply_When_RequestBelongsToTenancyWhiteList()
      throws ServletException, IOException {
    TenantContext.setCurrentTenant(99L);
    Mockito.when(request.getRequestURI()).thenReturn("/actuator/health/liveness");
    Mockito.doAnswer(invocation -> {
      assertThat(TenantContext.getCurrentTenant()).isNull();
      TenantContext.setCurrentTenant(8L);
      return null;
    }).when(filterChain).doFilter(request, response);

    httpTenantFilter.doFilterInternal(request, response, filterChain);

    Mockito.verifyNoInteractions(tenantResolverService);
    assertThat(TenantContext.getCurrentTenant()).isNull();
  }

  @Test
  void clearsTenantWhenAWhitelistedHandlerThrows() throws ServletException, IOException {
    var failure = new ServletException("synthetic handler failure");
    Mockito.when(request.getRequestURI()).thenReturn("/actuator/health/liveness");
    Mockito.doAnswer(invocation -> {
      TenantContext.setCurrentTenant(8L);
      throw failure;
    }).when(filterChain).doFilter(request, response);

    assertThatThrownBy(() -> httpTenantFilter.doFilterInternal(request, response, filterChain))
        .isSameAs(failure);

    Mockito.verifyNoInteractions(tenantResolverService);
    assertThat(TenantContext.getCurrentTenant()).isNull();
  }

  @Test
  void clearsTenantWhenResolutionStopsTheRequest() {
    var failure = new IllegalStateException("synthetic resolver failure");
    TenantContext.setCurrentTenant(99L);
    Mockito.when(request.getRequestURI()).thenReturn("/agencies/1");
    Mockito.when(tenantResolverService.resolve(request)).thenAnswer(invocation -> {
      assertThat(TenantContext.getCurrentTenant()).isNull();
      TenantContext.setCurrentTenant(8L);
      throw failure;
    });

    assertThatThrownBy(() -> httpTenantFilter.doFilterInternal(request, response, filterChain))
        .isSameAs(failure);

    Mockito.verifyNoInteractions(filterChain);
    assertThat(TenantContext.getCurrentTenant()).isNull();
  }

  @Test
  void doFilterInternal_Should_Apply_When_DoesNotBelongBelongsToTenancyWhiteList()
      throws ServletException, IOException {
    Mockito.when(request.getRequestURI()).thenReturn("/agencies/1");
    Mockito.when(tenantResolverService.resolve(request)).thenReturn(7L);
    Mockito.doAnswer(invocation -> {
      assertThat(TenantContext.getCurrentTenant()).isEqualTo(7L);
      return null;
    }).when(filterChain).doFilter(request, response);

    httpTenantFilter.doFilterInternal(request, response, filterChain);

    Mockito.verify(tenantResolverService).resolve(request);
    assertThat(TenantContext.getCurrentTenant()).isNull();
  }

  @Test
  void doFilterInternal_Should_ClearTheTenant_When_TheChainThrows()
      throws ServletException, IOException {
    Mockito.when(request.getRequestURI()).thenReturn("/agencies/1");
    Mockito.when(tenantResolverService.resolve(request)).thenReturn(7L);
    Mockito.doThrow(new ServletException("boom")).when(filterChain).doFilter(request, response);

    try {
      assertThatThrownBy(() -> httpTenantFilter.doFilterInternal(request, response, filterChain))
          .isInstanceOf(ServletException.class);

      assertThat(TenantContext.getCurrentTenant()).isNull();
    } finally {
      TenantContext.clear();
    }
  }
}
