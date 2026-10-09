package com.mindone.editor.simulation.service;

import com.mindone.editor.simulation.dto.NodeDemand;
import com.mindone.editor.simulation.dto.SimulationAnalysisResponse;
import com.mindone.editor.simulation.repository.SimulationAnalysisRepository;
import com.mindone.editor.simulation.support.AnalDateTimeParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관망해석 시뮬레이션 분석시각 조회 서비스.
 *
 * <p>분석일시(분 단위)를 받아 정수장 송수유량 / 펌프조합 / 노드별 수요량을 공유 EMS DB 에서 함께 조회한다.</p>
 */
@Service
public class SimulationAnalysisService {

    private final SimulationAnalysisRepository repository;

    /** 정수장 송수유량 태그번호(환경별 오버라이드 가능). */
    private final String outFlowTag;

    public SimulationAnalysisService(SimulationAnalysisRepository repository,
                                     @Value("${editor.simulation.out-flow-tag}") String outFlowTag) {
        this.repository = repository;
        this.outFlowTag = outFlowTag;
    }

    /**
     * 분석일시 기준으로 송수유량 / 펌프조합 / 노드별 수요량을 조회한다.
     *
     * @param analDateTime 분석일시 문자열(형식 {@code yyyy-MM-dd HH:mm})
     * @return 분석시각 조회 응답(각 항목은 해당 분 데이터가 없으면 null)
     * @throws com.mindone.editor.common.exception.RestApiException 분석일시 형식이 잘못되면(파싱은 {@link AnalDateTimeParser} 위임)
     */
    @Transactional(readOnly = true)
    public SimulationAnalysisResponse analyze(String analDateTime) {
        LocalDateTime ts = AnalDateTimeParser.parse(analDateTime);

        Long outFlow = repository.findOutFlow(outFlowTag, ts);
        String pumpComb = repository.findPumpComb(ts);
        List<NodeDemand> demands = repository.findDemands(ts);

        return new SimulationAnalysisResponse(outFlow, pumpComb, demands);
    }
}
