package com.mo.swtp.ems.wnp.dto;

import com.mo.swtp.ems.support.ColLength;
import com.mo.swtp.ems.support.UseYn;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 수계통지점 단건 수정 요청 — <b>식별자를 담지 않는다.</b>
 *
 * <p>수정 대상은 경로 변수가 정한다. 본문에도 ID를 두면 둘이 어긋났을 때 어느 쪽이 이기는지가
 * 구현 세부에 달린다.
 */
@Schema(description = "수계통지점 단건 수정 요청. 대상은 경로가 정하므로 식별자를 담지 않는다")
public record WnpModifyRequest(

        @Schema(description = "수계통지점명. 생략하면 바뀌지 않는다. 공백만으로 채울 수는 없다")
        @Size(max = ColLength.WNP_NM)
        // null은 "변경하지 않음"이라 @NotBlank를 쓸 수 없다. 값이 왔을 때만 공백만인 것을 막는다 —
        // 컬럼이 NOT NULL이지만 PostgreSQL은 빈 문자열을 받으므로 DB가 걸러 주지 않는다.
        @Pattern(regexp = ".*\\S.*", message = "공백만으로 채울 수 없다")
        String wnpNm,

        @Schema(description = "사용여부. 생략하면 바뀌지 않는다. N으로 내리는 것이 이 도메인의 삭제다")
        UseYn useYn,

        @Schema(description = "정렬순서. 생략하면 바뀌지 않는다", example = "1")
        Integer sortOrd) {
}
