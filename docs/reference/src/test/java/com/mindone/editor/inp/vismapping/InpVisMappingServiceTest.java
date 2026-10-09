package com.mindone.editor.inp.vismapping;

import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.common.exception.error.CommonErrorCode;
import com.mindone.editor.inp.exception.InpFileErrorCode;
import com.mindone.editor.common.domain.YesOrNo;
import com.mindone.editor.inp.repository.InpFileRepository;
import com.mindone.editor.inp.vismapping.domain.InpVisMapping;
import com.mindone.editor.inp.vismapping.dto.InpVisMappingRequest;
import com.mindone.editor.inp.vismapping.dto.InpVisMappingResponse;
import com.mindone.editor.inp.vismapping.exception.InpVisMappingErrorCode;
import com.mindone.editor.inp.vismapping.repository.InpVisMappingRepository;
import com.mindone.editor.inp.vismapping.service.InpVisMappingService;
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
 * INP 시각화 매핑 서비스 단위 검증 — 등록/중복/필수값/조회/수정/삭제.
 *
 * <p>DB 없이 리포지토리를 목으로 대체하고, 분기 로직(파일 존재·필수값·중복)만 빠르게 검증한다.</p>
 */
class InpVisMappingServiceTest {

    private static final String FILE_ID = "file-1";

    private InpVisMappingRepository mappingRepository;
    private InpFileRepository fileRepository;
    private InpVisMappingService inpVisMappingService;

    @BeforeEach
    void setUp() {
        mappingRepository = mock(InpVisMappingRepository.class);
        fileRepository = mock(InpFileRepository.class);
        when(mappingRepository.save(any(InpVisMapping.class))).thenAnswer(inv -> inv.getArgument(0));
        inpVisMappingService = new InpVisMappingService(mappingRepository, fileRepository);
    }

    private InpVisMappingRequest request(String junctionId, String pipeId,
                                         String flowTagNo, String pressureTagNo, String pointNm, Integer sortOrd, YesOrNo dispYn) {
        return new InpVisMappingRequest(junctionId, pipeId, flowTagNo, pressureTagNo, pointNm, sortOrd, dispYn);
    }

    @Test
    @DisplayName("등록: 필수값/태그/지점명이 저장되고, 선택값 공백은 null 로 정규화된다")
    void createNormalizesAndSaves() {
        when(fileRepository.existsById(FILE_ID)).thenReturn(true);
        when(mappingRepository.existsByInpFileIdAndJunctionId(FILE_ID, "J-101")).thenReturn(false);

        InpVisMappingResponse res = inpVisMappingService.create(
                FILE_ID, request("  J-101 ", "P-205", "FT-101", "   ", null, 0, YesOrNo.Y));

        assertThat(res.inpFileId()).isEqualTo(FILE_ID);
        assertThat(res.junctionId()).isEqualTo("J-101");   // trim 됨
        assertThat(res.pipeId()).isEqualTo("P-205");
        assertThat(res.flowTagNo()).isEqualTo("FT-101");
        assertThat(res.pressureTagNo()).isNull();           // 공백 → null
        assertThat(res.pointNm()).isNull();                 // null → null
        verify(mappingRepository).save(any(InpVisMapping.class));
    }

    @Test
    @DisplayName("등록: 대상 INP 파일이 없으면 FILE_NOT_FOUND")
    void createWithMissingFileThrows() {
        when(fileRepository.existsById(FILE_ID)).thenReturn(false);

        assertThatThrownBy(() -> inpVisMappingService.create(FILE_ID, request("J-101", "P-1", null, null, null, 0, YesOrNo.Y)))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InpFileErrorCode.FILE_NOT_FOUND);
    }

    @Test
    @DisplayName("등록: junction 이 비면 INVALID_PARAMETER")
    void createWithBlankJunctionThrows() {
        when(fileRepository.existsById(FILE_ID)).thenReturn(true);

        assertThatThrownBy(() -> inpVisMappingService.create(FILE_ID, request("  ", "P-1", null, null, null, 0, YesOrNo.Y)))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("등록: 같은 파일에 같은 junction 이 이미 있으면 DUPLICATE_MAPPING")
    void createDuplicateJunctionThrows() {
        when(fileRepository.existsById(FILE_ID)).thenReturn(true);
        when(mappingRepository.existsByInpFileIdAndJunctionId(FILE_ID, "J-101")).thenReturn(true);

        assertThatThrownBy(() -> inpVisMappingService.create(FILE_ID, request("J-101", "P-1", null, null, null, 0, YesOrNo.Y)))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InpVisMappingErrorCode.DUPLICATE_MAPPING);
    }

    @Test
    @DisplayName("목록: 등록순으로 매핑을 반환한다")
    void listReturnsMappings() {
        when(fileRepository.existsById(FILE_ID)).thenReturn(true);
        when(mappingRepository.findByInpFileIdOrderByMappingIdAsc(FILE_ID)).thenReturn(List.of(
                InpVisMapping.create(FILE_ID, "J-1", "P-1", null, null, "지점1", 0, YesOrNo.Y),
                InpVisMapping.create(FILE_ID, "J-2", "P-2", null, null, "지점2", 0, YesOrNo.Y)));

        List<InpVisMappingResponse> res = inpVisMappingService.list(FILE_ID);

        assertThat(res).hasSize(2);
        assertThat(res).extracting(InpVisMappingResponse::junctionId).containsExactly("J-1", "J-2");
    }

    @Test
    @DisplayName("수정: 내용이 갱신되고, 다른 행의 junction 과 충돌하면 DUPLICATE_MAPPING")
    void updateMutatesAndChecksDuplicate() {
        InpVisMapping existing = InpVisMapping.create(FILE_ID, "J-1", "P-1", null, null, "옛지점", 0, YesOrNo.Y);
        when(mappingRepository.findByMappingIdAndInpFileId(10L, FILE_ID)).thenReturn(Optional.of(existing));
        when(mappingRepository.existsByInpFileIdAndJunctionIdAndMappingIdNot(FILE_ID, "J-9", 10L)).thenReturn(false);

        InpVisMappingResponse res = inpVisMappingService.update(
                FILE_ID, 10L, request("J-9", "P-9", "FT-9", "PT-9", "새지점", 0, YesOrNo.Y));

        assertThat(res.junctionId()).isEqualTo("J-9");
        assertThat(res.pipeId()).isEqualTo("P-9");
        assertThat(res.pointNm()).isEqualTo("새지점");
        assertThat(existing.getJunctionId()).isEqualTo("J-9"); // 영속 엔티티가 직접 변경됨

        // 다른 행과 junction 충돌 시 예외
        when(mappingRepository.existsByInpFileIdAndJunctionIdAndMappingIdNot(FILE_ID, "J-9", 10L)).thenReturn(true);
        assertThatThrownBy(() -> inpVisMappingService.update(FILE_ID, 10L, request("J-9", "P-9", null, null, null, 0, YesOrNo.Y)))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InpVisMappingErrorCode.DUPLICATE_MAPPING);
    }

    @Test
    @DisplayName("조회/삭제: 해당 파일 소속 매핑이 없으면 MAPPING_NOT_FOUND")
    void missingMappingThrows() {
        when(mappingRepository.findByMappingIdAndInpFileId(eq(99L), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inpVisMappingService.get(FILE_ID, 99L))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InpVisMappingErrorCode.MAPPING_NOT_FOUND);

        assertThatThrownBy(() -> inpVisMappingService.delete(FILE_ID, 99L))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InpVisMappingErrorCode.MAPPING_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제: 존재하는 매핑은 리포지토리 delete 가 호출된다")
    void deleteRemovesMapping() {
        InpVisMapping existing = InpVisMapping.create(FILE_ID, "J-1", "P-1", null, null, null, 0, YesOrNo.Y);
        when(mappingRepository.findByMappingIdAndInpFileId(10L, FILE_ID)).thenReturn(Optional.of(existing));

        inpVisMappingService.delete(FILE_ID, 10L);

        verify(mappingRepository).delete(existing);
    }
}
