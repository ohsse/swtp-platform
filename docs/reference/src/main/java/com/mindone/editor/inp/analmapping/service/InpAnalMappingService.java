package com.mindone.editor.inp.analmapping.service;

import com.mindone.editor.common.domain.YesOrNo;
import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.common.exception.error.CommonErrorCode;
import com.mindone.editor.inp.analmapping.domain.InpAnalMapping;
import com.mindone.editor.inp.analmapping.dto.InpAnalMappingRequest;
import com.mindone.editor.inp.analmapping.dto.InpAnalMappingResponse;
import com.mindone.editor.inp.analmapping.exception.InpAnalMappingErrorCode;
import com.mindone.editor.inp.analmapping.repository.InpAnalMappingRepository;
import com.mindone.editor.inp.exception.InpFileErrorCode;
import com.mindone.editor.inp.opt.domain.DataType;
import com.mindone.editor.inp.repository.InpFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * INP 분석 매핑 서비스.
 *
 * <p>특정 INP 파일에 대한 분석 매핑(node + 태그번호 + 데이터유형)을 등록/조회/수정/삭제한다.
 * 한 파일에 여러 매핑을 둘 수 있으며(1:N), 같은 파일 안에서 같은 node + 데이터유형의 중복 매핑은 막는다.</p>
 *
 * <p>node ID 가 실제 INP 파일에 존재하는지는 검증하지 않는다(입력값을 그대로 저장). 대신 매핑이 가리키는
 * 부모 INP 파일 자체의 존재 여부는 검증한다.</p>
 */
@Service
@RequiredArgsConstructor
public class InpAnalMappingService {

    private final InpAnalMappingRepository inpAnalMappingRepository;
    private final InpFileRepository inpFileRepository;

    /**
     * 매핑을 등록한다.
     *
     * @param inpFileId 대상 INP 파일 ID
     * @param request   매핑 요청(node/태그번호 필수, 데이터유형은 태그번호로 결정)
     * @return 등록된 매핑
     * @throws RestApiException 파일이 없으면 {@link InpFileErrorCode#FILE_NOT_FOUND},
     *                          필수값이 비었거나 태그번호로 데이터유형을 결정할 수 없으면 {@link CommonErrorCode#INVALID_PARAMETER},
     *                          같은 node + 데이터유형 매핑이 이미 있으면 {@link InpAnalMappingErrorCode#DUPLICATE_MAPPING}
     */
    @Transactional
    public InpAnalMappingResponse create(String inpFileId, InpAnalMappingRequest request) {
        requireFile(inpFileId);
        String nodeId = requireText(request.nodeId());
        String tagNo = requireText(request.tagNo());
        DataType dataType = deriveDataType(tagNo);
        YesOrNo analYn = defaultYes(request.analYn());

        if (inpAnalMappingRepository.existsByInpFileIdAndNodeIdAndDataType(inpFileId, nodeId, dataType)) {
            throw new RestApiException(InpAnalMappingErrorCode.DUPLICATE_MAPPING);
        }

        InpAnalMapping mapping = InpAnalMapping.create(inpFileId, nodeId, tagNo, dataType, analYn);
        return InpAnalMappingResponse.from(inpAnalMappingRepository.save(mapping));
    }

    /**
     * 특정 INP 파일의 매핑 목록을 등록순(매핑 ID 오름차순)으로 조회한다.
     *
     * @param inpFileId 대상 INP 파일 ID
     * @return 매핑 목록 (없으면 빈 목록)
     * @throws RestApiException 파일이 없으면 {@link InpFileErrorCode#FILE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public List<InpAnalMappingResponse> list(String inpFileId) {
        requireFile(inpFileId);
        return inpAnalMappingRepository.findByInpFileIdOrderByMappingIdAsc(inpFileId)
                .stream()
                .map(InpAnalMappingResponse::from)
                .toList();
    }

    /**
     * 단일 매핑을 조회한다(해당 INP 파일 소속 검증 포함).
     *
     * @param inpFileId 대상 INP 파일 ID
     * @param mappingId 매핑 ID
     * @return 매핑
     * @throws RestApiException 매핑이 없거나 파일 소속이 아니면 {@link InpAnalMappingErrorCode#MAPPING_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public InpAnalMappingResponse get(String inpFileId, Long mappingId) {
        return InpAnalMappingResponse.from(requireMapping(inpFileId, mappingId));
    }

    /**
     * 매핑을 수정한다(소속 INP 파일은 변경하지 않음).
     *
     * @param inpFileId 대상 INP 파일 ID
     * @param mappingId 매핑 ID
     * @param request   수정 요청
     * @return 수정된 매핑
     * @throws RestApiException 매핑이 없으면 {@link InpAnalMappingErrorCode#MAPPING_NOT_FOUND},
     *                          필수값이 비었거나 태그번호로 데이터유형을 결정할 수 없으면 {@link CommonErrorCode#INVALID_PARAMETER},
     *                          같은 node + 데이터유형 매핑이 (자신 외에) 이미 있으면 {@link InpAnalMappingErrorCode#DUPLICATE_MAPPING}
     */
    @Transactional
    public InpAnalMappingResponse update(String inpFileId, Long mappingId, InpAnalMappingRequest request) {
        InpAnalMapping mapping = requireMapping(inpFileId, mappingId);
        String nodeId = requireText(request.nodeId());
        String tagNo = requireText(request.tagNo());
        DataType dataType = deriveDataType(tagNo);
        YesOrNo analYn = defaultYes(request.analYn());

        if (inpAnalMappingRepository.existsByInpFileIdAndNodeIdAndDataTypeAndMappingIdNot(inpFileId, nodeId, dataType, mappingId)) {
            throw new RestApiException(InpAnalMappingErrorCode.DUPLICATE_MAPPING);
        }

        mapping.update(nodeId, tagNo, dataType, analYn);
        return InpAnalMappingResponse.from(mapping);
    }

    /**
     * 매핑을 삭제한다.
     *
     * @param inpFileId 대상 INP 파일 ID
     * @param mappingId 매핑 ID
     * @throws RestApiException 매핑이 없거나 파일 소속이 아니면 {@link InpAnalMappingErrorCode#MAPPING_NOT_FOUND}
     */
    @Transactional
    public void delete(String inpFileId, Long mappingId) {
        InpAnalMapping mapping = requireMapping(inpFileId, mappingId);
        inpAnalMappingRepository.delete(mapping);
    }

    // ===== 내부 헬퍼 =====

    /** 부모 INP 파일이 존재하는지 검증한다(없으면 예외). */
    private void requireFile(String inpFileId) {
        if (!inpFileRepository.existsById(inpFileId)) {
            throw new RestApiException(InpFileErrorCode.FILE_NOT_FOUND);
        }
    }

    /** 해당 INP 파일에 속한 매핑을 조회하거나 없으면 예외. */
    private InpAnalMapping requireMapping(String inpFileId, Long mappingId) {
        return inpAnalMappingRepository.findByMappingIdAndInpFileId(mappingId, inpFileId)
                .orElseThrow(() -> new RestApiException(InpAnalMappingErrorCode.MAPPING_NOT_FOUND));
    }

    /** 필수 문자열을 검증하고 trim 해서 반환한다(비어 있으면 예외). */
    private String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
        }
        return value.trim();
    }

    /**
     * 태그번호 규칙에 따라 데이터유형을 결정한다.
     *
     * <p>태그번호에 {@code FRI} 가 포함되면 {@link DataType#FLOW}, {@code PRI} 가 포함되면 {@link DataType#PRESSURE}
     * 로 결정한다(대소문자 무시). 둘 다 포함되면 {@code FRI}(FLOW) 를 우선한다. 어느 쪽도 없으면
     * {@link CommonErrorCode#INVALID_PARAMETER} 예외를 던진다.</p>
     */
    private DataType deriveDataType(String tagNo) {
        String upper = tagNo.toUpperCase();
        if (upper.contains("FRI")) {
            return DataType.FLOW;
        }
        if (upper.contains("PRI")) {
            return DataType.PRESSURE;
        }
        throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
    }

    /** 분석여부 기본값을 적용한다(미지정 시 {@link YesOrNo#Y}). */
    private YesOrNo defaultYes(YesOrNo value) {
        return value == null ? YesOrNo.Y : value;
    }
}
