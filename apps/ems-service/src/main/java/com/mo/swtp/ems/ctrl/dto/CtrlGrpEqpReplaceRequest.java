package com.mo.swtp.ems.ctrl.dto;

import java.util.List;

import com.mo.swtp.ems.support.ColLength;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 제어그룹의 설비 편성을 통째로 교체하는 요청.
 *
 * <p><b>빈 배열이 유효하다</b> — 편성 해제이자, 단독키 PK가 만든 "비활성 그룹이 설비를 인질로 잡는"
 * 상황에서 빠져나가는 수단이다(문서 03 「결정 3」). 그래서 {@code @NotEmpty}가 아니라 {@code @NotNull}이다.
 *
 * <p>배열의 <b>순서가 곧 정렬순서</b>다(1부터). 재정렬 전용 API를 두지 않는 이유가 이것이다.
 *
 * <p>여기 걸린 것은 형식 검증이지 존재 검증이 아니다 — {@code eqpId}가 master에 실재하는지는
 * 확인하지 않는다(문서 01 「한계 4」 승계).
 */
@Schema(description = "설비 편성 전체 교체 요청")
public record CtrlGrpEqpReplaceRequest(

        @Schema(description = """
                편성할 설비ID 목록. 이 배열이 편성의 전부가 되며 순서가 곧 정렬순서(1부터)다. \
                빈 배열은 편성 해제다. 같은 값을 두 번 담으면 409다.""")
        @NotNull
        List<@NotBlank @Size(max = ColLength.EQP_ID) String> eqpIds) {
}
