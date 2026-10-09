package com.mo.swtp.ems.ctrl.dto;

import com.mo.swtp.ems.support.ColLength;
import com.mo.swtp.ems.support.UseYn;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 일괄 수정의 한 항목 — 단건과 달리 <b>식별자를 담는다.</b>
 *
 * <p>일괄 경로에는 대상을 가리킬 경로 변수가 없으므로 본문이 유일한 출처다.
 * 즉 ID를 담는 이유가 단건과 반대다 — 그래서 {@link CtrlGrpModifyRequest}와 타입을 나눈다.
 */
@Schema(description = "일괄 수정의 한 항목. 일괄 경로에는 경로 변수가 없으므로 항목마다 식별자를 담는다")
public record CtrlGrpModifyItemRequest(

        @Schema(description = "수정할 제어그룹ID. 없는 값이 하나라도 있으면 요청 전체가 404다")
        @NotBlank
        @Size(max = ColLength.CTRL_GRP_ID)
        String ctrlGrpId,

        @Schema(description = "제어그룹명. 생략하면 바뀌지 않는다. 공백만으로 채울 수는 없다")
        @Size(max = ColLength.CTRL_GRP_NM)
        // null은 "변경하지 않음"이라 @NotBlank를 쓸 수 없다. 값이 왔을 때만 공백만인 것을 막는다 —
        // 컬럼이 NOT NULL이지만 PostgreSQL은 빈 문자열을 받으므로 DB가 걸러 주지 않는다.
        @Pattern(regexp = ".*\\S.*", message = "공백만으로 채울 수 없다")
        String ctrlGrpNm,

        @Schema(description = "사용여부. 생략하면 바뀌지 않는다")
        UseYn useYn,

        @Schema(description = "정렬순서. 생략하면 바뀌지 않는다", example = "1")
        Integer sortOrd) {
}
