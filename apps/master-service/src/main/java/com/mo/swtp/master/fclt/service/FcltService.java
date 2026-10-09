package com.mo.swtp.master.fclt.service;

import com.mo.swtp.master.fclt.repository.FcltRepository;
import com.mo.swtp.common.api.BusinessException;
import com.mo.swtp.common.api.CommonErrorCode;
import com.mo.swtp.master.support.UseYn;
import com.mo.swtp.master.fclt.domain.Fclt;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.mo.swtp.master.fclt.dto.FcltAddListRequest;
import com.mo.swtp.master.fclt.dto.FcltAddRequest;
import com.mo.swtp.master.fclt.dto.FcltModifyListRequest;
import com.mo.swtp.master.fclt.dto.FcltModifyRequest;
import com.mo.swtp.master.fclt.dto.FcltResponse;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 시설 CRUD.
 *
 * <p><b>목록 조회는 비어 있어도 예외를 던지지 않는다.</b> 지목한 자원이 없는 것(단건 조회·수정 대상)만
 * 404이고, 목록이 0건인 것은 정상 상태다 — 신규 정수장은 기준정보 0건에서 출발한다(05 「결정 2」).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FcltService {

    private final FcltRepository fcltRepository;


    /** 목록 조회 — 비어 있으면 빈 배열이다(404가 아니다) */
    public List<FcltResponse> getFcltList(UseYn useYn) {
        var fclts = useYn != null ? fcltRepository.findAllByUseYnOrderBySortOrdAsc(useYn) : fcltRepository.findAllByOrderBySortOrdAsc();
        return convert(fclts, FcltResponse::from);
    }

    /** 단건 조회 — 지목한 자원이 없는 것은 실제 오류이므로 404다 */
    public FcltResponse getFcltById(String fcltId) {
        Fclt fclt = getFcltEntity(fcltId);
        return FcltResponse.from(fclt);
    }

    /** 시설타입별 목록 — 목록이므로 비어 있으면 빈 배열이다 */
    public List<FcltResponse> getFcltByFcltTypeCd(String fcltTypeCd) {
        return convert(fcltRepository.findAllByFcltTypeCdOrderBySortOrdAsc(fcltTypeCd), FcltResponse::from);
    }

    @Transactional
    public FcltResponse addFclt(FcltAddRequest request) {
        Fclt fclt = new Fclt(request.fcltId(), request.fcltNm(), request.fcltTypeCd(), request.sortOrd(), request.useYn());
        return FcltResponse.from(fcltRepository.save(fclt));
    }

    @Transactional
    public List<FcltResponse> addFclts(FcltAddListRequest request) {
        List<Fclt> fclts = request.requests().stream()
                .map(item -> new Fclt(item.fcltId(), item.fcltNm(), item.fcltTypeCd(), item.sortOrd(), item.useYn()))
                .toList();

        return convert(fcltRepository.saveAll(fclts), FcltResponse::from);
    }

    @Transactional
    public FcltResponse modifyFclt(FcltModifyRequest request) {
        Fclt fclt = getFcltEntity(request.fcltId());
        fclt.replace(request.fcltNm(), request.fcltTypeCd(), request.sortOrd(), request.useYn());
        return FcltResponse.from(fclt);
    }


    @Transactional
    public List<FcltResponse> modifyFclts(FcltModifyListRequest request) {
        List<FcltModifyRequest> requests = request.requests();

        List<String> fcltIds = requests.stream()
                .map(FcltModifyRequest::fcltId)
                .toList();

        List<Fclt> fclts = fcltRepository.findAllById(fcltIds);

        if (fclts.size() != fcltIds.size()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }

        Map<String, FcltModifyRequest> requestByFcltId = requests.stream()
                .collect(Collectors.toMap(FcltModifyRequest::fcltId, Function.identity()));

        fclts.forEach(fclt -> {
            FcltModifyRequest item = requestByFcltId.get(fclt.getFcltId());
            fclt.replace(item.fcltNm(), item.fcltTypeCd(), item.sortOrd(), item.useYn());
        });

        return fclts.stream()
                .map(FcltResponse::from)
                .toList();
    }


    private <R> List<R> convert(List<Fclt> fcltList, Function<Fclt, R> mapper) {
        return fcltList.stream().map(mapper).toList();
    }

    private Fclt getFcltEntity(String fcltId) {
        return fcltRepository.findById(fcltId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
