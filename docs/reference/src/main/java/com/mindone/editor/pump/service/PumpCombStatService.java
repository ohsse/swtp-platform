package com.mindone.editor.pump.service;

import com.mindone.editor.pump.dto.PumpCombStatResponse;
import com.mindone.editor.pump.repository.PumpCombStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 펌프조합별 운영 현황 / 전력 원단위 조회 서비스.
 *
 * <p>{@code pump_comb_m}(펌프조합 마스터)에 저장된 값을 그대로 표출한다. 운영건수(분)·전력원단위 등 통계는
 * 외부 EMS/AI 프로세스가 미리 적재해 두므로, 이 조회는 조회 시점에 집계하지 않는다(가벼운 단순 조회).</p>
 */
@Service
@RequiredArgsConstructor
public class PumpCombStatService {

    private final PumpCombStatRepository pumpCombStatRepository;

    /**
     * 펌프조합별 운영 현황 / 전력 원단위를 조회한다.
     *
     * @return 조합별 집계(운영대수→조합 순)
     */
    @Transactional(readOnly = true)
    public List<PumpCombStatResponse> stats() {
        return pumpCombStatRepository.findCombStats();
    }
}
