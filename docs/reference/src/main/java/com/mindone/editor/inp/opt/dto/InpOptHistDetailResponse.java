package com.mindone.editor.inp.opt.dto;

import com.mindone.editor.inp.opt.domain.InpFileOptHist;
import com.mindone.editor.inp.opt.domain.OptimizeStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 최적화 이력 상세 응답.
 *
 * <p>이력 메타 정보와 함께, 최적화 전(prevResultSnap)/후(resultSnap) 결과를
 * 지점명 + 데이터유형 기준으로 병합한 비교 행 목록({@link #comparisons})을 제공한다.</p>
 *
 * @param histId         이력 ID
 * @param inpFileId      INP 파일 ID
 * @param statusCd       진행상태코드(READY/RUNNING/COMPLETED/ERROR)
 * @param prevRevNo      최적화 이전 개정번호
 * @param revNo          최적화 이후 개정번호
 * @param strtDttm       시작일시
 * @param endDttm        종료일시
 * @param optionSnap     최적화 실행 시점의 분석 매핑 설정 스냅샷
 * @param comparisons    최적화 전/후 결과 비교 행 목록
 */
@Schema(description = "최적화 이력 상세 응답")
public record InpOptHistDetailResponse(
        @Schema(description = "이력 ID") Long histId,
        @Schema(description = "INP 파일 ID") String inpFileId,
        @Schema(description = "진행상태코드(READY/RUNNING/COMPLETED/ERROR)") OptimizeStatus statusCd,
        @Schema(description = "최적화 이전 개정번호") Integer prevRevNo,
        @Schema(description = "최적화 이후 개정번호") Integer revNo,
        @Schema(description = "시작일시") LocalDateTime strtDttm,
        @Schema(description = "종료일시") LocalDateTime endDttm,
        @Schema(description = "분석 매핑 설정 스냅샷") List<InpAnalMappingSnap> optionSnap,
        @Schema(description = "최적화 전/후 결과 비교 행 목록") List<OptResultComparison> comparisons
) {

    /**
     * 최적화 이력 엔티티로부터 상세 응답 DTO 를 생성한다.
     *
     * <p>분석 매핑에는 별도의 정렬순서가 없으므로 설정 스냅샷({@code optionSnap})은 등록순
     * (매핑 ID 오름차순)으로 정렬한다.</p>
     *
     * <p>비교 행({@code comparisons})은 시각화 매핑(vis_mapping) 중 표시여부(disp_yn)=Y 인
     * 지점만 정렬순서(sort_ord)대로 포함한다. {@code displayOrderByPointNm} 은 그 표시 지점의
     * 지점명 → 순번 매핑이며(결과 스냅샷의 지점명이 시각화 매핑의 지점명과 매칭됨), 이 매핑에
     * 없는(=표시 대상이 아닌) 지점의 결과는 비교 행에서 제외한다.</p>
     *
     * @param hist                  최적화 이력 엔티티
     * @param displayOrderByPointNm 표시 지점(disp_yn=Y) 지점명 → 정렬순서 매핑
     */
    public static InpOptHistDetailResponse from(InpFileOptHist hist, Map<String, Integer> displayOrderByPointNm) {
        List<InpAnalMappingSnap> sortedOptionSnap = sortByMappingId(hist.getOptionSnap());

        // 결과를 (지점명+데이터유형) 으로 병합·정렬한 뒤, 표시 지점(disp_yn=Y)에 해당하는 행만 남긴다.
        List<OptResultComparison> comparisons =
                OptResultComparison.merge(hist.getPrevResultSnap(), hist.getResultSnap(), displayOrderByPointNm).stream()
                        .filter(c -> displayOrderByPointNm.containsKey(c.pointNm()))
                        .toList();

        return new InpOptHistDetailResponse(
                hist.getHistId(),
                hist.getInpFileId(),
                hist.getStatusCd(),
                hist.getPrevRevNo(),
                hist.getRevNo(),
                hist.getStrtDttm(),
                hist.getEndDttm(),
                sortedOptionSnap,
                comparisons
        );
    }

    /** 설정 스냅샷을 등록순(매핑 ID 오름차순)으로 정렬한다(매핑 ID 가 없는 항목은 뒤로). */
    private static List<InpAnalMappingSnap> sortByMappingId(List<InpAnalMappingSnap> snaps) {
        if (snaps == null) {
            return List.of();
        }
        return snaps.stream()
                .sorted(Comparator.comparing(InpAnalMappingSnap::mappingId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }
}
