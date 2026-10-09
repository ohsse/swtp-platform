package com.mo.swtp.master.drvmd.service;

import com.mo.swtp.common.api.BusinessException;
import com.mo.swtp.common.api.CommonErrorCode;
import com.mo.swtp.common.operation.ControlTargetType;
import com.mo.swtp.master.drvmd.dto.DrvmdChgResponse;
import com.mo.swtp.master.drvmd.dto.DrvmdModeResponse;
import com.mo.swtp.master.drvmd.repository.DrvmdChgRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 운전모드 변경이력 조회.
 *
 * <p>{@code @Transactional(readOnly = true)}만 있고 쓰기 메서드가 없다 — 이 서비스에 write가 없는 것은
 * 계약이다(02 결정 1). 모드를 바꾸는 서비스가 자기 트랜잭션에서 이력을 함께 남긴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DrvmdChgService {

    /** 한 번에 내려주는 이력 행수의 상한. 이력 테이블은 무한 증가하므로 무제한 조회를 열어 두지 않는다. */
    private static final int MAX_LIMIT = 1_000;

    private final DrvmdChgRepository drvmdChgRepository;

    /**
     * 대상별 이력 목록 — 최신순.
     *
     * <p><b>결과가 비어도 예외를 던지지 않는다.</b> 같은 앱의 다른 도메인(`PrcsService` 등)은 빈 목록을
     * {@link CommonErrorCode#NOT_FOUND}로 바꾸지만 여기서는 의미가 다르다 — 기준정보가 하나도 없는 것은
     * 대개 잘못된 조회이나, <b>이력이 없는 것은 "아직 한 번도 안 바뀜"이라는 정상 상태</b>다(03 결정 5).
     * 404로 바꾸면 프론트가 정상 상태를 오류로 표시하게 된다.
     */
    public List<DrvmdChgResponse> getHistory(ControlTargetType ctrlTrgtType, String ctrlTrgtId,
                                             LocalDateTime from, LocalDateTime to, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        return drvmdChgRepository.findHistory(ctrlTrgtType, ctrlTrgtId, from, to, limit);
    }

    /**
     * {@code at} 시각의 운전모드. {@code at}이 없으면 현재 모드다.
     *
     * <p>"현재"를 별도 개념으로 두지 않는 것이 결정 5의 요지다 — 현재는 지금 시각을 기준으로 한 과거 조회다.
     * <b>기준 시각까지</b> 이력이 없으면 초기값 {@link com.mo.swtp.common.operation.DrivenMode#AI_ANLS}로
     * 답하되 {@code initialDefault}로 그 사실을 함께 알린다 — {@code at}이 과거이면 그 뒤에 전환 이력이
     * 있을 수 있으므로 "한 번도 바뀐 적 없음"이 아니다.
     */
    public DrvmdModeResponse getMode(ControlTargetType ctrlTrgtType, String ctrlTrgtId, LocalDateTime at) {
        LocalDateTime when = (at != null) ? at : LocalDateTime.now();
        return drvmdChgRepository.findLatestAt(ctrlTrgtType, ctrlTrgtId, when)
                .map(DrvmdModeResponse::from)
                .orElseGet(() -> DrvmdModeResponse.initial(ctrlTrgtType, ctrlTrgtId));
    }
}
