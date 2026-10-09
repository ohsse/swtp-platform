package com.mo.swtp.master.fclt.dto;

import com.mo.swtp.master.support.ColLength;
import com.mo.swtp.master.support.UseYn;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 시설 수정 요청 — 단건 경로와 일괄 경로가 <b>이 record 하나를 공유한다.</b>
 *
 * <p>그래서 식별자를 담는다. 일괄 경로에는 대상을 가리킬 경로 변수가 없어 본문이 유일한 출처이기 때문인데,
 * 그 결과 단건 경로에서도 본문의 식별자가 이기고 {@code PUT /{fcltId}}의 경로 변수가 무시된다
 * (05 「알려진 한계」 1). ems는 같은 자리에서 record를 둘로 쪼개 이 문제를 없앴다.
 */
@Schema(description = "시설 수정 요청. 단건·일괄 경로가 공유하므로 식별자를 담는다")
public record FcltModifyRequest(

        @Schema(description = "수정할 시설ID. 이것이 대상을 정한다 — 단건 경로에서도 경로 변수가 아니라 이 값이 이긴다")
        @NotBlank
        @Size(max = ColLength.FCLT_ID)
        String fcltId,

        @Schema(description = "시설명. 생략하면 바뀌지 않는다. 공백만으로 채울 수는 없다")
        @Size(max = ColLength.FCLT_NM)
        // null은 "변경하지 않음"이라 @NotBlank를 쓸 수 없다. 값이 왔을 때만 공백만인 것을 막는다 —
        // 컬럼이 NOT NULL이지만 PostgreSQL은 빈 문자열을 받으므로 DB가 걸러 주지 않는다.
        @Pattern(regexp = ".*\\S.*", message = "공백만으로 채울 수 없다")
        String fcltNm,

        @Schema(description = "시설타입코드. 생략하면 바뀌지 않는다. 공백만으로 채울 수는 없다")
        @Size(max = ColLength.FCLT_TYPE_CD)
        @Pattern(regexp = ".*\\S.*", message = "공백만으로 채울 수 없다")
        String fcltTypeCd,

        @Schema(description = "정렬순서. 생략하면 바뀌지 않는다", example = "1")
        Integer sortOrd,

        @Schema(description = "사용여부. 생략하면 바뀌지 않는다. N으로 내리는 것이 이 도메인의 삭제다")
        UseYn useYn) {}
