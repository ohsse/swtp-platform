package com.mo.swtp.ems.ctrl.dto;

import java.util.List;

import com.mo.swtp.ems.support.ColLength;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 제어그룹의 태그 편성을 통째로 교체하는 요청.
 *
 * <p>빈 배열이 유효하다(편성 해제). 배열의 순서가 곧 정렬순서다.
 */
@Schema(description = "태그 편성 전체 교체 요청")
public record CtrlGrpTagReplaceRequest(

        @Schema(description = """
                편성할 태그시리얼번호 목록. 이 배열이 편성의 전부가 되며 순서가 곧 정렬순서(1부터)다. \
                빈 배열은 편성 해제다. 같은 값을 두 번 담으면 409다.""")
        @NotNull
        List<@NotBlank @Size(max = ColLength.TAG_SN) String> tagSns) {
}
