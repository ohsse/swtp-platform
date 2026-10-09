package com.mindone.editor.common.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * REST API 공통 응답 래퍼.
 *
 * <p>모든 응답은 처리 결과를 나타내는 {@code code} 와 실제 데이터를 담는 {@code data} 로 구성된다.
 * 성공 시 {@code code} 는 {@code "SUCCESS"}, 실패 시 {@link com.mindone.editor.common.exception.error.ErrorCode}
 * 의 이름이 담긴다.</p>
 *
 * @param <T> 응답 데이터 타입
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Schema(description = "공통 응답")
public class ResponseObject<T> {

    @Schema(description = "응답 코드 (성공: SUCCESS, 실패: 에러 코드명)")
    private String code;

    @Schema(description = "응답 데이터")
    private T data;
}
