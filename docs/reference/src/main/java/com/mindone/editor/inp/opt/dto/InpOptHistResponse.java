package com.mindone.editor.inp.opt.dto;

import com.mindone.editor.inp.opt.domain.InpFileOptHist;
import com.mindone.editor.inp.opt.domain.OptimizeStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 최적화 이력 목록 응답.
 *
 * <p>진행률({@link #progressRate})은 진행세대수({@link #implGenerCount}) /
 * 총세대수({@link #totGenerCount}) * 100 으로 계산한 백분율(소수 첫째자리 반올림)이다.
 * 총세대수가 없거나 0 이면 0 으로 처리한다.</p>
 *
 * @param histId         이력 ID
 * @param inpFileId      INP 파일 ID
 * @param statusCd       진행상태코드(READY/RUNNING/COMPLETED/ERROR)
 * @param totGenerCount  총세대수
 * @param implGenerCount 진행세대수
 * @param progressRate   진행률(%, 진행세대수/총세대수 * 100)
 * @param prevRevNo      최적화 이전 개정번호
 * @param revNo          최적화 이후 개정번호
 * @param strtDttm       시작일시
 * @param endDttm        종료일시
 */
@Schema(description = "최적화 이력 목록 응답")
public record InpOptHistResponse(
        @Schema(description = "이력 ID") Long histId,
        @Schema(description = "INP 파일 ID") String inpFileId,
        @Schema(description = "진행상태코드(READY/RUNNING/COMPLETED/ERROR)") OptimizeStatus statusCd,
        @Schema(description = "총세대수") Integer totGenerCount,
        @Schema(description = "진행세대수") Integer implGenerCount,
        @Schema(description = "진행률(%, 진행세대수/총세대수 * 100)") BigDecimal progressRate,
        @Schema(description = "최적화 이전 개정번호") Integer prevRevNo,
        @Schema(description = "최적화 이후 개정번호") Integer revNo,
        @Schema(description = "시작일시") LocalDateTime strtDttm,
        @Schema(description = "종료일시") LocalDateTime endDttm
) {

    /**
     * 최적화 이력 엔티티로부터 응답 DTO 를 생성한다.
     */
    public static InpOptHistResponse from(InpFileOptHist hist) {
        return new InpOptHistResponse(
                hist.getHistId(),
                hist.getInpFileId(),
                hist.getStatusCd(),
                hist.getTotGenerCount(),
                hist.getImplGenerCount(),
                calcProgressRate(hist.getImplGenerCount(), hist.getTotGenerCount()),
                hist.getPrevRevNo(),
                hist.getRevNo(),
                hist.getStrtDttm(),
                hist.getEndDttm()
        );
    }

    /**
     * 진행률(%)을 계산한다. 총세대수가 없거나 0 이면 0 을 반환한다.
     *
     * @param impl 진행세대수
     * @param tot  총세대수
     * @return 진행세대수/총세대수 * 100 (소수 첫째자리 반올림)
     */
    private static BigDecimal calcProgressRate(Integer impl, Integer tot) {
        if (tot == null || tot <= 0) {
            return BigDecimal.ZERO;
        }
        int implValue = (impl == null) ? 0 : impl;
        return BigDecimal.valueOf(implValue)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(tot), 1, RoundingMode.HALF_UP);
    }
}
