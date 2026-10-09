package com.mindone.editor.inp.vismapping.service;

import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.common.exception.error.CommonErrorCode;
import com.mindone.editor.inp.exception.InpFileErrorCode;
import com.mindone.editor.inp.repository.InpFileRepository;
import com.mindone.editor.inp.vismapping.domain.InpVisMapping;
import com.mindone.editor.inp.vismapping.dto.InpVisMappingRequest;
import com.mindone.editor.inp.vismapping.dto.InpVisMappingResponse;
import com.mindone.editor.inp.vismapping.exception.InpVisMappingErrorCode;
import com.mindone.editor.inp.vismapping.repository.InpVisMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * INP 시각화 매핑 서비스.
 *
 * <p>특정 INP 파일에 대한 시각화 매핑(junction + pipe + 유량태그 + 압력태그 + 지점명)을 등록/조회/수정/삭제한다.
 * 한 파일에 여러 지점을 둘 수 있으며(1:N), 같은 파일 안에서 같은 junction 의 중복 매핑은 막는다.</p>
 *
 * <p>junction/pipe ID 가 실제 INP 파일에 존재하는지는 검증하지 않는다(입력값을 그대로 저장). 대신 매핑이 가리키는
 * 부모 INP 파일 자체의 존재 여부는 검증한다.</p>
 */
@Service
@RequiredArgsConstructor
public class InpVisMappingService {

    private final InpVisMappingRepository inpVisMappingRepository;
    private final InpFileRepository inpFileRepository;

    /**
     * 매핑을 등록한다.
     *
     * @param inpFileId 대상 INP 파일 ID
     * @param request   매핑 요청(junction/pipe 필수, 태그/지점명 선택)
     * @return 등록된 매핑
     * @throws RestApiException 파일이 없으면 {@link InpFileErrorCode#FILE_NOT_FOUND},
     *                          필수값이 비었으면 {@link CommonErrorCode#INVALID_PARAMETER},
     *                          같은 junction 매핑이 이미 있으면 {@link InpVisMappingErrorCode#DUPLICATE_MAPPING}
     */
    @Transactional
    public InpVisMappingResponse create(String inpFileId, InpVisMappingRequest request) {
        requireFile(inpFileId);
        String junctionId = requireText(request.junctionId());
        String pipeId = requireText(request.pipeId());

        if (inpVisMappingRepository.existsByInpFileIdAndJunctionId(inpFileId, junctionId)) {
            throw new RestApiException(InpVisMappingErrorCode.DUPLICATE_MAPPING);
        }

        InpVisMapping mapping = InpVisMapping.create(
                inpFileId, junctionId, pipeId,
                normalize(request.flowTagNo()), normalize(request.pressureTagNo()), normalize(request.pointNm()),
                request.sortOrd(), request.dispYn()
        );
        return InpVisMappingResponse.from(inpVisMappingRepository.save(mapping));
    }

    /**
     * 특정 INP 파일의 매핑 목록을 등록순(매핑 ID 오름차순)으로 조회한다.
     *
     * @param inpFileId 대상 INP 파일 ID
     * @return 매핑 목록 (없으면 빈 목록)
     * @throws RestApiException 파일이 없으면 {@link InpFileErrorCode#FILE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public List<InpVisMappingResponse> list(String inpFileId) {
        requireFile(inpFileId);
        return inpVisMappingRepository.findByInpFileIdOrderByMappingIdAsc(inpFileId)
                .stream()
                .map(InpVisMappingResponse::from)
                .toList();
    }

    /**
     * 단일 매핑을 조회한다(해당 INP 파일 소속 검증 포함).
     *
     * @param inpFileId 대상 INP 파일 ID
     * @param mappingId 매핑 ID
     * @return 매핑
     * @throws RestApiException 매핑이 없거나 파일 소속이 아니면 {@link InpVisMappingErrorCode#MAPPING_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public InpVisMappingResponse get(String inpFileId, Long mappingId) {
        return InpVisMappingResponse.from(requireMapping(inpFileId, mappingId));
    }

    /**
     * 매핑을 수정한다(소속 INP 파일은 변경하지 않음).
     *
     * @param inpFileId 대상 INP 파일 ID
     * @param mappingId 매핑 ID
     * @param request   수정 요청
     * @return 수정된 매핑
     * @throws RestApiException 매핑이 없으면 {@link InpVisMappingErrorCode#MAPPING_NOT_FOUND},
     *                          필수값이 비었으면 {@link CommonErrorCode#INVALID_PARAMETER},
     *                          같은 junction 매핑이 (자신 외에) 이미 있으면 {@link InpVisMappingErrorCode#DUPLICATE_MAPPING}
     */
    @Transactional
    public InpVisMappingResponse update(String inpFileId, Long mappingId, InpVisMappingRequest request) {
        InpVisMapping mapping = requireMapping(inpFileId, mappingId);
        String junctionId = requireText(request.junctionId());
        String pipeId = requireText(request.pipeId());

        if (inpVisMappingRepository.existsByInpFileIdAndJunctionIdAndMappingIdNot(inpFileId, junctionId, mappingId)) {
            throw new RestApiException(InpVisMappingErrorCode.DUPLICATE_MAPPING);
        }

        mapping.update(junctionId, pipeId,
                normalize(request.flowTagNo()), normalize(request.pressureTagNo()), normalize(request.pointNm()),
                request.sortOrd(), request.dispYn());
        return InpVisMappingResponse.from(mapping);
    }

    /**
     * 매핑을 삭제한다.
     *
     * @param inpFileId 대상 INP 파일 ID
     * @param mappingId 매핑 ID
     * @throws RestApiException 매핑이 없거나 파일 소속이 아니면 {@link InpVisMappingErrorCode#MAPPING_NOT_FOUND}
     */
    @Transactional
    public void delete(String inpFileId, Long mappingId) {
        InpVisMapping mapping = requireMapping(inpFileId, mappingId);
        inpVisMappingRepository.delete(mapping);
    }

    // ===== 내부 헬퍼 =====

    /** 부모 INP 파일이 존재하는지 검증한다(없으면 예외). */
    private void requireFile(String inpFileId) {
        if (!inpFileRepository.existsById(inpFileId)) {
            throw new RestApiException(InpFileErrorCode.FILE_NOT_FOUND);
        }
    }

    /** 해당 INP 파일에 속한 매핑을 조회하거나 없으면 예외. */
    private InpVisMapping requireMapping(String inpFileId, Long mappingId) {
        return inpVisMappingRepository.findByMappingIdAndInpFileId(mappingId, inpFileId)
                .orElseThrow(() -> new RestApiException(InpVisMappingErrorCode.MAPPING_NOT_FOUND));
    }

    /** 필수 문자열을 검증하고 trim 해서 반환한다(비어 있으면 예외). */
    private String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
        }
        return value.trim();
    }

    /** 선택 문자열을 정규화한다(앞뒤 공백 제거, 비어 있으면 {@code null}). */
    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
