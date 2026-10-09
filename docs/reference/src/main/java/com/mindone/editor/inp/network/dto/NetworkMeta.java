package com.mindone.editor.inp.network.dto;

import com.mindone.editor.inp.network.model.NetworkBounds;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * 네트워크 상세조회 메타데이터.
 *
 * <p>좌표계/단위/문자셋 등 표출에 필요한 컨텍스트와, 레이어별 개수·전 섹션 행 수·경계·경고를 담는다.</p>
 *
 * @param inpFileId        INP 파일 ID
 * @param orgnlFileNm      원본 파일명
 * @param detectedCharset  실제 디코딩에 사용된 문자셋(예: MS949, UTF-8)
 * @param crs              좌표계 식별자(미지정 시 {@code UNKNOWN}) — 좌표는 재투영하지 않은 투영좌표
 * @param flowUnits        유량 단위(OPTIONS UNITS, 예: CMH)
 * @param headloss         손실식(OPTIONS HEADLOSS, 예: H-W)
 * @param demandModel      수요 모델(OPTIONS DEMAND MODEL, 예: PDA)
 * @param quality          수질 옵션(OPTIONS QUALITY, 예: NONE)
 * @param layerCounts      레이어별 객체 수(junctions/reservoirs/tanks/pipes/pumps/valves/labels)
 * @param sectionRowCounts 전 섹션 데이터 행 수(완전 파싱 가시화)
 * @param bounds           좌표/정점/라벨로 계산한 경계(없으면 null)
 * @param backdropBounds   {@code [BACKDROP] DIMENSIONS} 경계(없으면 null)
 * @param warnings         좌표 누락 등 경고 목록
 */
@Schema(description = "네트워크 상세조회 메타데이터")
public record NetworkMeta(
        @Schema(description = "INP 파일 ID") String inpFileId,
        @Schema(description = "원본 파일명") String orgnlFileNm,
        @Schema(description = "디코딩에 사용된 문자셋", example = "MS949") String detectedCharset,
        @Schema(description = "좌표계 식별자(미지정 시 UNKNOWN, 좌표는 투영좌표 원본)", example = "UNKNOWN") String crs,
        @Schema(description = "유량 단위", example = "CMH") String flowUnits,
        @Schema(description = "손실식", example = "H-W") String headloss,
        @Schema(description = "수요 모델", example = "PDA") String demandModel,
        @Schema(description = "수질 옵션", example = "NONE") String quality,
        @Schema(description = "레이어별 객체 수") Map<String, Integer> layerCounts,
        @Schema(description = "전 섹션 데이터 행 수") Map<String, Integer> sectionRowCounts,
        @Schema(description = "좌표 경계(투영좌표)") NetworkBounds bounds,
        @Schema(description = "BACKDROP 경계") NetworkBounds backdropBounds,
        @Schema(description = "경고 목록") List<String> warnings
) {
}
