package com.mindone.editor.inp.opt.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mindone.editor.inp.opt.domain.DataType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "결과값")
public record ResultSnap(
        @Schema(description = "계측시간")  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime measureDttm,
        @Schema(description = "지점명") String pointNm,
        @Schema(description = "데이터유형") DataType dataType,
        @Schema(description = "계측값") BigDecimal measureValue,
        @Schema(description = "분석값") BigDecimal analValue,
        @Schema(description = "오차율") BigDecimal errorRate
        ) {
}