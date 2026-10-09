package com.mo.swtp.master.eqp.service;

import com.mo.swtp.master.eqp.repository.EqpRepository;
import com.mo.swtp.common.api.BusinessException;
import com.mo.swtp.common.api.CommonErrorCode;
import com.mo.swtp.master.support.UseYn;
import com.mo.swtp.master.eqp.domain.Eqp;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.mo.swtp.master.eqp.dto.EqpAddListRequest;
import com.mo.swtp.master.eqp.dto.EqpAddRequest;
import com.mo.swtp.master.eqp.dto.EqpModifyListRequest;
import com.mo.swtp.master.eqp.dto.EqpModifyRequest;
import com.mo.swtp.master.eqp.dto.EqpResponse;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 설비 CRUD.
 *
 * <p><b>목록 조회는 비어 있어도 예외를 던지지 않는다.</b> 지목한 자원이 없는 것(단건 조회·수정 대상)만
 * 404이고, 목록이 0건인 것은 정상 상태다 — 신규 정수장은 기준정보 0건에서 출발한다(05 「결정 2」).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EqpService {

    private final EqpRepository eqpRepository;


    /** 목록 조회 — 비어 있으면 빈 배열이다(404가 아니다) */
    public List<EqpResponse> getEqpList(UseYn useYn) {
        var eqps = useYn != null ? eqpRepository.findAllByUseYnOrderBySortOrdAsc(useYn) : eqpRepository.findAllByOrderBySortOrdAsc();
        return convert(eqps, EqpResponse::from);
    }

    /** 단건 조회 — 지목한 자원이 없는 것은 실제 오류이므로 404다 */
    public EqpResponse getEqpById(String eqpId) {
        Eqp eqp = getEqpEntity(eqpId);
        return EqpResponse.from(eqp);
    }

    /** 설비타입별 목록 — 목록이므로 비어 있으면 빈 배열이다 */
    public List<EqpResponse> getEqpByEqpTypeCd(String eqpTypeCd) {
        return convert(eqpRepository.findAllByEqpTypeCdOrderBySortOrdAsc(eqpTypeCd), EqpResponse::from);
    }

    /** 시설별 목록 — 시설ID가 없는 값이어도 빈 배열이다. 시설의 존재는 검사하지 않는다 */
    public List<EqpResponse> getEqpByFcltId(String fcltId) {
        return convert(eqpRepository.findAllByFcltIdOrderBySortOrdAsc(fcltId), EqpResponse::from);
    }

    @Transactional
    public EqpResponse addEqp(EqpAddRequest request) {
        Eqp eqp = new Eqp(request.eqpId(), request.fcltId(), request.eqpNm(), request.eqpTypeCd(), request.useYn(), request.sortOrd());
        return EqpResponse.from(eqpRepository.save(eqp));
    }

    @Transactional
    public List<EqpResponse> addEqps(EqpAddListRequest request) {
        List<Eqp> eqps = request.requests().stream()
                .map(item -> new Eqp(item.eqpId(), item.fcltId(), item.eqpNm(), item.eqpTypeCd(), item.useYn(), item.sortOrd()))
                .toList();

        return convert(eqpRepository.saveAll(eqps), EqpResponse::from);
    }

    @Transactional
    public EqpResponse modifyEqp(EqpModifyRequest request) {
        Eqp eqp = getEqpEntity(request.eqpId());
        eqp.replace(request.fcltId(), request.eqpNm(), request.eqpTypeCd(), request.useYn(), request.sortOrd());
        return EqpResponse.from(eqp);
    }


    @Transactional
    public List<EqpResponse> modifyEqps(EqpModifyListRequest request) {
        List<EqpModifyRequest> requests = request.requests();

        List<String> eqpIds = requests.stream()
                .map(EqpModifyRequest::eqpId)
                .toList();

        List<Eqp> eqps = eqpRepository.findAllById(eqpIds);

        if (eqps.size() != eqpIds.size()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }

        Map<String, EqpModifyRequest> requestByEqpId = requests.stream()
                .collect(Collectors.toMap(EqpModifyRequest::eqpId, Function.identity()));

        eqps.forEach(eqp -> {
            EqpModifyRequest item = requestByEqpId.get(eqp.getEqpId());
            eqp.replace(item.fcltId(), item.eqpNm(), item.eqpTypeCd(), item.useYn(), item.sortOrd());
        });

        return eqps.stream()
                .map(EqpResponse::from)
                .toList();
    }


    private <R> List<R> convert(List<Eqp> eqpList, Function<Eqp, R> mapper) {
        return eqpList.stream().map(mapper).toList();
    }

    private Eqp getEqpEntity(String eqpId) {
        return eqpRepository.findById(eqpId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
