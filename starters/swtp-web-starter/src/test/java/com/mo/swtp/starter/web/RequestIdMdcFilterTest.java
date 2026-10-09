package com.mo.swtp.starter.web;

import java.util.concurrent.atomic.AtomicReference;

import com.mo.swtp.common.observability.SwtpHeaders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestIdMdcFilterTest {

    private final RequestIdMdcFilter filter = new RequestIdMdcFilter();

    /** 체인 실행 중 MDC에 실제로 올라간 값을 포착한다 */
    private String runAndCaptureMdc(MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        filter.doFilter(request, response,
                (req, res) -> captured.set(MDC.get(SwtpHeaders.REQUEST_ID_MDC_KEY)));
        return captured.get();
    }

    @Test
    @DisplayName("게이트웨이가 전파한 요청 ID를 MDC에 올린다 — 게이트웨이 로그와 같은 ID로 묶인다")
    void putsForwardedRequestIdIntoMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/master/sample-items");
        request.addHeader(SwtpHeaders.REQUEST_ID, "gateway-issued-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(runAndCaptureMdc(request, response)).isEqualTo("gateway-issued-id");
        assertThat(response.getHeader(SwtpHeaders.REQUEST_ID)).isEqualTo("gateway-issued-id");
    }

    @Test
    @DisplayName("게이트웨이를 거치지 않은 직접 호출도 ID를 발급받는다 — ID 없는 로그를 만들지 않는다")
    void generatesRequestIdForDirectCall() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/master/sample-items");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String captured = runAndCaptureMdc(request, response);

        assertThat(captured).isNotBlank();
        assertThat(response.getHeader(SwtpHeaders.REQUEST_ID)).isEqualTo(captured);
    }

    @Test
    @DisplayName("요청 종료 후 MDC를 정리한다 — 스레드 풀 재사용 시 이전 요청 ID 누출 방지")
    void clearsMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/master/sample-items");
        request.addHeader(SwtpHeaders.REQUEST_ID, "some-id");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        assertThat(MDC.get(SwtpHeaders.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("체인에서 예외가 나도 MDC를 정리한다")
    void clearsMdcWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/master/sample-items");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> {
                    throw new IllegalStateException("처리 실패");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(SwtpHeaders.REQUEST_ID_MDC_KEY)).isNull();
    }
}
