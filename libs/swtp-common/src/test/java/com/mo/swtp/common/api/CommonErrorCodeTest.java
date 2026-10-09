package com.mo.swtp.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonErrorCodeTest {

    @Test
    @DisplayName("공통 에러 코드는 'COMMON-<HTTP상태>' 형식을 따른다")
    void codeFormat() {
        for (CommonErrorCode errorCode : CommonErrorCode.values()) {
            assertEquals("COMMON-" + errorCode.getHttpStatus(), errorCode.getCode());
            assertTrue(errorCode.getHttpStatus() >= 400 && errorCode.getHttpStatus() <= 599);
        }
    }

    @Test
    @DisplayName("BusinessException의 detail은 선택 — 없으면 null, 로그 메시지는 코드값으로 대체된다")
    void businessExceptionDetail() {
        BusinessException withoutDetail = new BusinessException(CommonErrorCode.NOT_FOUND);
        assertNull(withoutDetail.getDetail());
        assertEquals(CommonErrorCode.NOT_FOUND.getCode(), withoutDetail.getMessage());

        BusinessException withDetail = new BusinessException(CommonErrorCode.NOT_FOUND, "sample-item 7 없음");
        assertEquals("sample-item 7 없음", withDetail.getDetail());
        assertEquals("sample-item 7 없음", withDetail.getMessage());
        assertEquals(CommonErrorCode.NOT_FOUND, withDetail.getErrorCode());
    }
}
