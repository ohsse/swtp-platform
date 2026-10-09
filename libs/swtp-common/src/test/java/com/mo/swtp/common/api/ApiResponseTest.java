package com.mo.swtp.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    @DisplayName("ok()는 성공 코드와 데이터를 담는다")
    void okCarriesData() {
        ApiResponse<String> response = ApiResponse.ok("hello");

        assertThat(response.code()).isEqualTo(ApiResponse.CODE_SUCCESS);
        assertThat(response.data()).isEqualTo("hello");
        assertThat(response.success()).isTrue();
    }

    @Test
    @DisplayName("of()는 지정한 코드와 데이터를 담는다")
    void ofCarriesCodeAndData() {
        ApiResponse<String> response = ApiResponse.of("E4001", "필수 값 누락");

        assertThat(response.code()).isEqualTo("E4001");
        assertThat(response.data()).isEqualTo("필수 값 누락");
        assertThat(response.success()).isFalse();
    }
}
