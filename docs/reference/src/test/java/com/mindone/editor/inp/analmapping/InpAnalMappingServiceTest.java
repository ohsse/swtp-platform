package com.mindone.editor.inp.analmapping;

import com.mindone.editor.common.domain.YesOrNo;
import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.common.exception.error.CommonErrorCode;
import com.mindone.editor.inp.analmapping.domain.InpAnalMapping;
import com.mindone.editor.inp.analmapping.dto.InpAnalMappingRequest;
import com.mindone.editor.inp.analmapping.dto.InpAnalMappingResponse;
import com.mindone.editor.inp.analmapping.exception.InpAnalMappingErrorCode;
import com.mindone.editor.inp.analmapping.repository.InpAnalMappingRepository;
import com.mindone.editor.inp.analmapping.service.InpAnalMappingService;
import com.mindone.editor.inp.exception.InpFileErrorCode;
import com.mindone.editor.inp.opt.domain.DataType;
import com.mindone.editor.inp.repository.InpFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * INP 분석 매핑 서비스 단위 검증 — 등록/중복/필수값/데이터유형 판별/분석여부/조회/수정/삭제.
 *
 * <p>DB 없이 리포지토리를 목으로 대체하고, 분기 로직(파일 존재·필수값·태그 기반 데이터유형·중복)만 빠르게 검증한다.</p>
 */
class InpAnalMappingServiceTest {

    private static final String FILE_ID = "file-1";

    private InpAnalMappingRepository mappingRepository;
    private InpFileRepository fileRepository;
    private InpAnalMappingService inpAnalMappingService;

    @BeforeEach
    void setUp() {
        mappingRepository = mock(InpAnalMappingRepository.class);
        fileRepository = mock(InpFileRepository.class);
        when(mappingRepository.save(any(InpAnalMapping.class))).thenAnswer(inv -> inv.getArgument(0));
        inpAnalMappingService = new InpAnalMappingService(mappingRepository, fileRepository);
    }

    private InpAnalMappingRequest request(String nodeId, String tagNo, YesOrNo analYn) {
        return new InpAnalMappingRequest(nodeId, tagNo, analYn);
    }

    @Test
    @DisplayName("등록: 필수값이 저장되고, 문자열 앞뒤 공백은 trim 된다")
    void createNormalizesAndSaves() {
        when(fileRepository.existsById(FILE_ID)).thenReturn(true);
        when(mappingRepository.existsByInpFileIdAndNodeIdAndDataType(FILE_ID, "J-101", DataType.FLOW)).thenReturn(false);

        InpAnalMappingResponse res = inpAnalMappingService.create(
                FILE_ID, request("  J-101 ", " FRI-101 ", YesOrNo.N));

        assertThat(res.inpFileId()).isEqualTo(FILE_ID);
        assertThat(res.nodeId()).isEqualTo("J-101");     // trim 됨
        assertThat(res.tagNo()).isEqualTo("FRI-101");    // trim 됨
        assertThat(res.dataType()).isEqualTo(DataType.FLOW); // 태그 FRI → FLOW
        assertThat(res.analYn()).isEqualTo(YesOrNo.N);
        verify(mappingRepository).save(any(InpAnalMapping.class));
    }

    @Test
    @DisplayName("등록: 분석여부 미지정 시 기본값 Y 로 저장된다")
    void createDefaultsAnalYnToYes() {
        when(fileRepository.existsById(FILE_ID)).thenReturn(true);

        InpAnalMappingResponse res = inpAnalMappingService.create(
                FILE_ID, request("J-101", "FRI-101", null));

        assertThat(res.analYn()).isEqualTo(YesOrNo.Y);
    }

    @Test
    @DisplayName("등록: 대상 INP 파일이 없으면 FILE_NOT_FOUND")
    void createWithMissingFileThrows() {
        when(fileRepository.existsById(FILE_ID)).thenReturn(false);

        assertThatThrownBy(() -> inpAnalMappingService.create(FILE_ID, request("J-101", "FRI-101", YesOrNo.Y)))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InpFileErrorCode.FILE_NOT_FOUND);
    }

    @Test
    @DisplayName("등록: node 가 비면 INVALID_PARAMETER")
    void createWithBlankNodeThrows() {
        when(fileRepository.existsById(FILE_ID)).thenReturn(true);

        assertThatThrownBy(() -> inpAnalMappingService.create(FILE_ID, request("  ", "FRI-101", YesOrNo.Y)))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("등록: 태그번호에 FRI/PRI 가 모두 없으면 INVALID_PARAMETER")
    void createWithUndeterminableDataTypeThrows() {
        when(fileRepository.existsById(FILE_ID)).thenReturn(true);

        assertThatThrownBy(() -> inpAnalMappingService.create(FILE_ID, request("J-101", "FT-101", YesOrNo.Y)))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("등록: 태그번호에 FRI 가 포함되면 FLOW 로 저장된다")
    void createDerivesFlowFromFriTag() {
        when(fileRepository.existsById(FILE_ID)).thenReturn(true);

        InpAnalMappingResponse res = inpAnalMappingService.create(
                FILE_ID, request("J-101", "FRI-2001", YesOrNo.Y));

        assertThat(res.dataType()).isEqualTo(DataType.FLOW);
        // 중복 검사도 판별된 FLOW 기준으로 수행
        verify(mappingRepository).existsByInpFileIdAndNodeIdAndDataType(FILE_ID, "J-101", DataType.FLOW);
    }

    @Test
    @DisplayName("등록: 태그번호에 PRI 가 포함되면 PRESSURE 로 저장된다")
    void createDerivesPressureFromPriTag() {
        when(fileRepository.existsById(FILE_ID)).thenReturn(true);

        InpAnalMappingResponse res = inpAnalMappingService.create(
                FILE_ID, request("J-101", "PRI-3002", YesOrNo.Y));

        assertThat(res.dataType()).isEqualTo(DataType.PRESSURE);
        verify(mappingRepository).existsByInpFileIdAndNodeIdAndDataType(FILE_ID, "J-101", DataType.PRESSURE);
    }

    @Test
    @DisplayName("등록: 같은 파일에 같은 node + 데이터유형이 이미 있으면 DUPLICATE_MAPPING")
    void createDuplicateThrows() {
        when(fileRepository.existsById(FILE_ID)).thenReturn(true);
        when(mappingRepository.existsByInpFileIdAndNodeIdAndDataType(FILE_ID, "J-101", DataType.FLOW)).thenReturn(true);

        assertThatThrownBy(() -> inpAnalMappingService.create(FILE_ID, request("J-101", "FRI-101", YesOrNo.Y)))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InpAnalMappingErrorCode.DUPLICATE_MAPPING);
    }

    @Test
    @DisplayName("목록: 등록순으로 매핑을 반환한다")
    void listReturnsMappings() {
        when(fileRepository.existsById(FILE_ID)).thenReturn(true);
        when(mappingRepository.findByInpFileIdOrderByMappingIdAsc(FILE_ID)).thenReturn(List.of(
                InpAnalMapping.create(FILE_ID, "J-1", "FRI-1", DataType.FLOW, YesOrNo.Y),
                InpAnalMapping.create(FILE_ID, "J-2", "PRI-2", DataType.PRESSURE, YesOrNo.N)));

        List<InpAnalMappingResponse> res = inpAnalMappingService.list(FILE_ID);

        assertThat(res).hasSize(2);
        assertThat(res).extracting(InpAnalMappingResponse::nodeId).containsExactly("J-1", "J-2");
        assertThat(res).extracting(InpAnalMappingResponse::dataType).containsExactly(DataType.FLOW, DataType.PRESSURE);
        assertThat(res).extracting(InpAnalMappingResponse::analYn).containsExactly(YesOrNo.Y, YesOrNo.N);
    }

    @Test
    @DisplayName("수정: 내용이 갱신되고(태그로 데이터유형 판별), 다른 행과 충돌하면 DUPLICATE_MAPPING")
    void updateMutatesAndChecksDuplicate() {
        InpAnalMapping existing = InpAnalMapping.create(FILE_ID, "J-1", "FRI-1", DataType.FLOW, YesOrNo.Y);
        when(mappingRepository.findByMappingIdAndInpFileId(10L, FILE_ID)).thenReturn(Optional.of(existing));
        when(mappingRepository.existsByInpFileIdAndNodeIdAndDataTypeAndMappingIdNot(FILE_ID, "J-9", DataType.PRESSURE, 10L)).thenReturn(false);

        InpAnalMappingResponse res = inpAnalMappingService.update(
                FILE_ID, 10L, request("J-9", "PRI-9", YesOrNo.N));

        assertThat(res.nodeId()).isEqualTo("J-9");
        assertThat(res.tagNo()).isEqualTo("PRI-9");
        assertThat(res.dataType()).isEqualTo(DataType.PRESSURE);  // 태그 PRI → PRESSURE
        assertThat(res.analYn()).isEqualTo(YesOrNo.N);
        assertThat(existing.getNodeId()).isEqualTo("J-9"); // 영속 엔티티가 직접 변경됨

        // 다른 행과 node + 데이터유형 충돌 시 예외
        when(mappingRepository.existsByInpFileIdAndNodeIdAndDataTypeAndMappingIdNot(FILE_ID, "J-9", DataType.PRESSURE, 10L)).thenReturn(true);
        assertThatThrownBy(() -> inpAnalMappingService.update(FILE_ID, 10L, request("J-9", "PRI-9", YesOrNo.N)))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InpAnalMappingErrorCode.DUPLICATE_MAPPING);
    }

    @Test
    @DisplayName("조회/삭제: 해당 파일 소속 매핑이 없으면 MAPPING_NOT_FOUND")
    void missingMappingThrows() {
        when(mappingRepository.findByMappingIdAndInpFileId(eq(99L), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inpAnalMappingService.get(FILE_ID, 99L))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InpAnalMappingErrorCode.MAPPING_NOT_FOUND);

        assertThatThrownBy(() -> inpAnalMappingService.delete(FILE_ID, 99L))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InpAnalMappingErrorCode.MAPPING_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제: 존재하는 매핑은 리포지토리 delete 가 호출된다")
    void deleteRemovesMapping() {
        InpAnalMapping existing = InpAnalMapping.create(FILE_ID, "J-1", "FRI-1", DataType.FLOW, YesOrNo.Y);
        when(mappingRepository.findByMappingIdAndInpFileId(10L, FILE_ID)).thenReturn(Optional.of(existing));

        inpAnalMappingService.delete(FILE_ID, 10L);

        verify(mappingRepository).delete(existing);
    }
}
