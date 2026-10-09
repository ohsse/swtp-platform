package com.mindone.editor.inp.opt.service;

import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.inp.analmapping.domain.InpAnalMapping;
import com.mindone.editor.inp.analmapping.exception.InpAnalMappingErrorCode;
import com.mindone.editor.inp.analmapping.repository.InpAnalMappingRepository;
import com.mindone.editor.inp.domain.InpFile;
import com.mindone.editor.inp.exception.InpFileErrorCode;
import com.mindone.editor.inp.opt.client.PythonOptClient;
import com.mindone.editor.inp.opt.domain.InpFileOptHist;
import com.mindone.editor.inp.opt.domain.OptimizeStatus;
import com.mindone.editor.inp.opt.dto.InpAnalMappingSnap;
import com.mindone.editor.inp.opt.dto.InpOptHistDetailResponse;
import com.mindone.editor.inp.opt.dto.InpOptHistResponse;
import com.mindone.editor.inp.opt.exception.InpOptErrorCode;
import com.mindone.editor.inp.opt.repository.InpOptHistRepository;
import com.mindone.editor.inp.repository.InpFileRepository;
import com.mindone.editor.common.domain.YesOrNo;
import com.mindone.editor.inp.vismapping.domain.InpVisMapping;
import com.mindone.editor.inp.vismapping.repository.InpVisMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InpOptHistService {

    private final InpFileRepository inpFileRepository;
    private final InpOptHistRepository inpOptHistRepository;
    private final InpAnalMappingRepository inpAnalMappingRepository;
    private final InpVisMappingRepository inpVisMappingRepository;
    private final PythonOptClient pythonOptClient;

    /**
     * 최적화 이력 목록을 조회한다.
     *
     * <p>{@code inpFileId}, {@code statusCd} 는 각각 {@code null} 이면 조건을 생략한다.
     * 응답에는 진행세대수/총세대수로 계산한 진행률이 포함된다.</p>
     *
     * @param inpFileId INP 파일 ID(없으면 전체)
     * @param statusCd  진행상태코드(없으면 전체)
     * @return 최적화 이력 응답 목록(최신순)
     */
    public List<InpOptHistResponse> getList (String inpFileId, OptimizeStatus statusCd) {
        return inpOptHistRepository.findAllByCondition(inpFileId, statusCd).stream()
                .map(InpOptHistResponse::from)
                .toList();
    }

    /**
     * 최적화 이력 상세를 조회한다.
     *
     * <p>최적화 전/후 결과 스냅샷을 지점명 + 데이터유형 기준으로 병합한 비교 행을 포함한다.
     * 비교 행은 해당 INP 파일의 시각화 매핑(vis_mapping) 중 표시여부(disp_yn)=Y 인 지점만
     * 정렬순서(sort_ord)대로 포함한다.</p>
     *
     * @param histId 이력 ID
     * @return 최적화 이력 상세 응답
     * @throws RestApiException 이력을 찾을 수 없는 경우({@link InpOptErrorCode#OPTIMIZE_HIST_NOT_FOUND})
     */
    public InpOptHistDetailResponse getDetail (Long histId) {
        InpFileOptHist hist = inpOptHistRepository.findById(histId)
                .orElseThrow(() -> new RestApiException(InpOptErrorCode.OPTIMIZE_HIST_NOT_FOUND));

        // 비교 행 정렬·필터 기준: 표시여부=Y 인 시각화 매핑 지점을 정렬순서대로 나열한 지점명 → 순번 매핑.
        Map<String, Integer> displayOrderByPointNm = buildDisplayOrderByPointNm(hist.getInpFileId());

        return InpOptHistDetailResponse.from(hist, displayOrderByPointNm);
    }

    /**
     * 시각화 매핑(vis_mapping)에서 표시여부(disp_yn)=Y 인 지점을 정렬순서(sort_ord) 오름차순으로
     * 나열해 지점명(point_nm) → 순번 매핑을 만든다.
     *
     * <p>정렬순서가 없는 항목은 뒤로 보내고, 동순위는 매핑 ID 오름차순(등록순)으로 보조 정렬한다.
     * 지점명이 없거나 중복인 항목은 먼저 나온 순번을 유지한다. 이 매핑의 지점명 집합이 곧 비교 행에
     * 포함할 표시 지점 집합이다(결과 스냅샷의 지점명이 시각화 매핑의 지점명과 매칭됨).</p>
     *
     * @param inpFileId INP 파일 ID
     * @return 표시 지점 지점명 → 정렬순서 매핑(정렬순서대로의 삽입 순서를 보존)
     */
    private Map<String, Integer> buildDisplayOrderByPointNm(String inpFileId) {
        List<InpVisMapping> visMappings = inpVisMappingRepository.findByInpFileIdOrderByMappingIdAsc(inpFileId);

        Map<String, Integer> orderByPointNm = new LinkedHashMap<>();
        int order = 0;
        // 매핑 ID 오름차순으로 조회된 목록을 정렬순서로 안정 정렬하므로, 동순위는 등록순이 유지된다.
        List<InpVisMapping> displaySorted = visMappings.stream()
                .filter(m -> m.getDispYn() == YesOrNo.Y)
                .sorted(Comparator.comparing(InpVisMapping::getSortOrd,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (InpVisMapping m : displaySorted) {
            if (m.getPointNm() != null && !orderByPointNm.containsKey(m.getPointNm())) {
                orderByPointNm.put(m.getPointNm(), order++);
            }
        }
        return orderByPointNm;
    }

    public Long run (String inpFileId) {
        /* INP 파일을 가져와서 현재 */
        InpFile inpFile = inpFileRepository.findById(inpFileId).orElse(null);

        if (inpFile == null) {
            throw new RestApiException(InpFileErrorCode.FILE_NOT_FOUND);
        }

        List<InpAnalMapping> inpMapping = inpAnalMappingRepository.findByInpFileIdOrderByMappingIdAsc(inpFileId);

        if (inpMapping == null || inpMapping.isEmpty()) {
            throw new RestApiException(InpAnalMappingErrorCode.MAPPING_NOT_FOUND);
        }

        /* 이력저장 */
        InpFileOptHist newOptHist = inpOptHistRepository.save(InpFileOptHist.create(inpFileId, convertMapping(inpMapping), inpFile.getCurrRevNo()));

        /* 이력 ID 발췌 */
        Long histId = newOptHist.getHistId();

        /*
         * 파이썬에 최적화 실행을 요청한다.
         * 파이썬은 이력 ID 로 별도 프로세스를 실행하므로 긴 처리 결과는 기다리지 않지만,
         * "요청 접수" 응답까지는 동기로 받아 접수 실패를 프론트로 전달한다.
         * (save() 가 자체 트랜잭션으로 이미 커밋되어 있어 파이썬이 해당 이력을 조회할 수 있다.)
         */
        try {
            pythonOptClient.requestOptimize(histId);
        } catch (Exception e) {
            // 요청 전달 실패 시 이력을 오류 상태로 변경하고, 프론트로 오류를 반환한다.
            newOptHist.markError();
            inpOptHistRepository.save(newOptHist);
            throw new RestApiException(InpOptErrorCode.OPTIMIZE_REQUEST_FAILED);
        }

        /* 프론트에는 이력 ID 를 반환한다. */
        return histId;
    }

    public List<InpAnalMappingSnap> convertMapping (List<InpAnalMapping> inpMappings) {
        List<InpAnalMappingSnap> inpMappingSnaps = new ArrayList<>();
        for (InpAnalMapping inpMapping : inpMappings) {
            inpMappingSnaps.add(new InpAnalMappingSnap(
               inpMapping.getMappingId(),
               inpMapping.getNodeId(),
               inpMapping.getTagNo(),
               inpMapping.getDataType(),
               inpMapping.getAnalYn()
            ));
        }
        return  inpMappingSnaps;
    }
}
