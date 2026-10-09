package com.mindone.editor.inp.network.service;

import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.inp.domain.InpFile;
import com.mindone.editor.inp.domain.InpFileRevision;
import com.mindone.editor.inp.exception.InpFileErrorCode;
import com.mindone.editor.inp.network.combiner.GeoJsonCombiner;
import com.mindone.editor.inp.network.combiner.NetworkAssembler;
import com.mindone.editor.inp.network.combiner.NonVisualSectionCombiner;
import com.mindone.editor.inp.network.combiner.OptionsCombiner;
import com.mindone.editor.inp.network.config.NetworkProperties;
import com.mindone.editor.inp.network.dto.NetworkDetailResponse;
import com.mindone.editor.inp.network.dto.NetworkLayers;
import com.mindone.editor.inp.network.dto.NetworkMeta;
import com.mindone.editor.inp.network.exception.InpNetworkErrorCode;
import com.mindone.editor.inp.network.intake.InpFileReader;
import com.mindone.editor.inp.network.intake.InpReadResult;
import com.mindone.editor.inp.network.model.LinkType;
import com.mindone.editor.inp.network.model.NetworkModel;
import com.mindone.editor.inp.network.model.NodeType;
import com.mindone.editor.inp.network.parser.InpParser;
import com.mindone.editor.inp.network.parser.InpSectionType;
import com.mindone.editor.inp.network.parser.ParsedInp;
import com.mindone.editor.inp.network.parser.SectionRow;
import com.mindone.editor.inp.repository.InpFileRepository;
import com.mindone.editor.inp.repository.InpFileRevisionRepository;
import com.mindone.editor.inp.storage.InpFileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * INP 상세조회 서비스 — 저장된 INP 파일을 읽어 파싱·결합하여 레이어별 GeoJSON 으로 반환한다.
 *
 * <p>요청 시마다 파일을 읽어 파싱하는 on-demand 방식(DB 영속화 없음)이다. 어떤 리비전을 읽을지는
 * 마스터의 현재 적용 리비전({@code curr_rev_no}) 또는 지정 리비전 번호로 결정한다.</p>
 *
 * <p>흐름: 마스터/리비전 조회 → {@link InpFileReader 읽기} → {@link InpParser 파싱}
 * → {@link NetworkAssembler 결합} → {@link GeoJsonCombiner GeoJSON 변환}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkService {

    private final InpFileRepository inpFileRepository;
    private final InpFileRevisionRepository inpFileRevisionRepository;
    private final InpFileStorage inpFileStorage;
    private final InpFileReader inpFileReader;
    private final InpParser inpParser;
    private final NetworkAssembler networkAssembler;
    private final GeoJsonCombiner geoJsonCombiner;
    private final NonVisualSectionCombiner nonVisualSectionCombiner;
    private final OptionsCombiner optionsCombiner;
    private final NetworkProperties networkProperties;

    /**
     * INP 파일의 <b>현재 적용 리비전</b> 네트워크를 상세조회한다.
     *
     * @param inpFileId INP 파일 ID
     * @return 메타 + 레이어별 GeoJSON
     * @throws RestApiException 파일 메타가 없으면 {@link InpFileErrorCode#FILE_NOT_FOUND},
     *                          현재 리비전이 없으면 {@link InpFileErrorCode#REVISION_NOT_FOUND},
     *                          물리 파일 읽기 실패 시 {@link InpNetworkErrorCode#FILE_READ_ERROR},
     *                          파싱 실패 시 {@link InpNetworkErrorCode#PARSE_FAILED},
     *                          네트워크 데이터가 전무하면 {@link InpNetworkErrorCode#NETWORK_EMPTY}
     */
    @Transactional(readOnly = true)
    public NetworkDetailResponse getNetwork(String inpFileId) {
        // 마스터와 현재 적용 리비전(curr_rev_no)을 단일 조인 쿼리로 함께 찾는다(PK 조건이라 0~1건).
        List<Object[]> rows = inpFileRepository.findWithCurrentRevisionById(inpFileId);
        if (rows.isEmpty()) {
            throw new RestApiException(InpFileErrorCode.FILE_NOT_FOUND);
        }
        Object[] row = rows.get(0);
        InpFile master = (InpFile) row[0];
        InpFileRevision current = (InpFileRevision) row[1];
        if (current == null) { // 마스터는 있으나 현재 리비전 행이 결손된 경우
            throw new RestApiException(InpFileErrorCode.REVISION_NOT_FOUND);
        }
        return buildNetwork(master, current.getStorFileNm());
    }

    /**
     * INP 파일의 <b>특정 리비전</b> 네트워크를 상세조회한다(과거 리비전 열람).
     *
     * @param inpFileId INP 파일 ID
     * @param revNo     리비전 번호
     * @return 메타 + 레이어별 GeoJSON
     * @throws RestApiException 파일 메타가 없으면 {@link InpFileErrorCode#FILE_NOT_FOUND},
     *                          리비전이 없으면 {@link InpFileErrorCode#REVISION_NOT_FOUND} 외 위와 동일
     */
    @Transactional(readOnly = true)
    public NetworkDetailResponse getNetwork(String inpFileId, int revNo) {
        InpFile master = requireMaster(inpFileId);
        InpFileRevision rev = requireRevision(inpFileId, revNo);
        return buildNetwork(master, rev.getStorFileNm());
    }

    /** 지정 저장 파일을 읽어 파싱·결합 후 상세조회 응답을 만든다. */
    private NetworkDetailResponse buildNetwork(InpFile master, String storFileNm) {
        InpReadResult read = readFile(storFileNm);

        try {
            ParsedInp parsed = inpParser.parse(read.lines());
            NetworkModel model = networkAssembler.assemble(parsed);

            if (isEmpty(model, parsed)) {
                throw new RestApiException(InpNetworkErrorCode.NETWORK_EMPTY);
            }

            NetworkLayers layers = geoJsonCombiner.toLayers(model);
            Map<String, Object> sections = nonVisualSectionCombiner.combine(parsed);
            // 설정(OPTIONS/TIMES/REACTIONS/ENERGY)은 별도 최상위 키가 아니라 sections 의 OPTIONS 키로 합류시킨다.
            sections.put(InpSectionType.OPTIONS.name(), optionsCombiner.combine(parsed));
            NetworkMeta meta = buildMeta(master, read, model, parsed);
            return new NetworkDetailResponse(meta, layers, sections);
        } catch (RestApiException e) {
            throw e; // 의미 있는 도메인 예외는 그대로 전파
        } catch (Exception e) {
            log.error("INP 파싱 실패: inpFileId={}, storFileNm={}, msg={}",
                    master.getInpFileId(), storFileNm, e.getMessage(), e);
            throw new RestApiException(InpNetworkErrorCode.PARSE_FAILED);
        }
    }

    /** 마스터를 조회하거나 없으면 예외. */
    private InpFile requireMaster(String inpFileId) {
        return inpFileRepository.findById(inpFileId)
                .orElseThrow(() -> new RestApiException(InpFileErrorCode.FILE_NOT_FOUND));
    }

    /** 특정 리비전을 조회하거나 없으면 예외. */
    private InpFileRevision requireRevision(String inpFileId, int revNo) {
        return inpFileRevisionRepository.findByInpFileIdAndRevNo(inpFileId, revNo)
                .orElseThrow(() -> new RestApiException(InpFileErrorCode.REVISION_NOT_FOUND));
    }

    /** 저장된 물리 파일을 바이트로 읽어 인코딩 자동판별 후 라인으로 디코딩한다. */
    private InpReadResult readFile(String storFileNm) {
        Resource resource = inpFileStorage.loadAsResource(storFileNm);
        try {
            return inpFileReader.read(resource.getContentAsByteArray());
        } catch (IOException e) {
            log.error("INP 물리 파일 읽기 실패: {} - {}", storFileNm, e.getMessage());
            throw new RestApiException(InpNetworkErrorCode.FILE_READ_ERROR);
        }
    }

    /** 노드/링크/라벨이 모두 없고 파싱된 행도 전혀 없으면 빈 네트워크로 간주한다. */
    private boolean isEmpty(NetworkModel model, ParsedInp parsed) {
        return model.getNodes().isEmpty()
                && model.getLinks().isEmpty()
                && model.getLabels().isEmpty()
                && parsed.totalRowCount() == 0;
    }

    /** 응답 메타를 조립한다. */
    private NetworkMeta buildMeta(InpFile master, InpReadResult read, NetworkModel model, ParsedInp parsed) {
        return new NetworkMeta(
                master.getInpFileId(),
                master.getOrgnlFileNm(),
                read.charsetName(),
                networkProperties.crsOrUnknown(),
                model.getOptions().flowUnits(),
                model.getOptions().headloss(),
                model.getOptions().demandModel(),
                model.getOptions().quality(),
                buildLayerCounts(model),
                buildSectionRowCounts(parsed),
                model.getBounds(),
                model.getBackdropBounds(),
                model.getWarnings()
        );
    }

    /** 레이어별 객체 수(슬라이드 4 레이어 순서 유지). */
    private Map<String, Integer> buildLayerCounts(NetworkModel model) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("junctions", (int) model.getNodes().stream().filter(n -> n.getType() == NodeType.JUNCTION).count());
        counts.put("reservoirs", (int) model.getNodes().stream().filter(n -> n.getType() == NodeType.RESERVOIR).count());
        counts.put("tanks", (int) model.getNodes().stream().filter(n -> n.getType() == NodeType.TANK).count());
        counts.put("pipes", (int) model.getLinks().stream().filter(l -> l.getType() == LinkType.PIPE).count());
        counts.put("pumps", (int) model.getLinks().stream().filter(l -> l.getType() == LinkType.PUMP).count());
        counts.put("valves", (int) model.getLinks().stream().filter(l -> l.getType() == LinkType.VALVE).count());
        counts.put("labels", model.getLabels().size());
        return counts;
    }

    /** 전 섹션 데이터 행 수(표준 섹션 + 미지 섹션) — 완전 파싱 가시화. 설정 섹션은 sections.OPTIONS 로 묶여 제외. */
    private Map<String, Integer> buildSectionRowCounts(ParsedInp parsed) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<InpSectionType, List<SectionRow>> e : parsed.sections().entrySet()) {
            // 설정 섹션(OPTIONS/TIMES/REACTIONS/ENERGY)은 sections.OPTIONS 로 묶여 표출되므로 카운트에서 제외
            if (NonVisualSectionCombiner.OPTION_SECTIONS.contains(e.getKey())) {
                continue;
            }
            counts.put(e.getKey().name(), e.getValue().size());
        }
        for (Map.Entry<String, List<SectionRow>> e : parsed.unknownSections().entrySet()) {
            counts.put("UNKNOWN:" + e.getKey(), e.getValue().size());
        }
        return counts;
    }
}
