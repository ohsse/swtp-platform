package com.mo.swtp.master.prcs.service;

import com.mo.swtp.master.prcs.repository.PrcsRepository;
import com.mo.swtp.common.api.BusinessException;
import com.mo.swtp.common.api.CommonErrorCode;
import com.mo.swtp.master.support.UseYn;
import com.mo.swtp.master.prcs.domain.Prcs;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.mo.swtp.master.prcs.dto.PrcsAddListRequest;
import com.mo.swtp.master.prcs.dto.PrcsAddRequest;
import com.mo.swtp.master.prcs.dto.PrcsModifyListRequest;
import com.mo.swtp.master.prcs.dto.PrcsModifyRequest;
import com.mo.swtp.master.prcs.dto.PrcsResponse;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 공정 CRUD.
 *
 * <p><b>목록 조회는 비어 있어도 예외를 던지지 않는다.</b> 지목한 자원이 없는 것(단건 조회·수정 대상)만
 * 404이고, 목록이 0건인 것은 정상 상태다 — 신규 정수장은 기준정보 0건에서 출발한다(05 「결정 2」).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrcsService {

    private final PrcsRepository prcsRepository;


    /** 목록 조회 — 비어 있으면 빈 배열이다(404가 아니다) */
    public List<PrcsResponse> getPrcsList(UseYn useYn) {
        var prcsEntities = useYn != null ? prcsRepository.findAllByUseYnOrderBySortOrdAsc(useYn) : prcsRepository.findAllByOrderBySortOrdAsc();
        return convert(prcsEntities, PrcsResponse::from);
    }

    /** 단건 조회 — 지목한 자원이 없는 것은 실제 오류이므로 404다 */
    public PrcsResponse getPrcsById(String prcsId) {
        Prcs prcs = getPrcsEntity(prcsId);
        return PrcsResponse.from(prcs);
    }

    /** 공정타입별 목록 — 목록이므로 비어 있으면 빈 배열이다 */
    public List<PrcsResponse> getPrcsByPrcsTypeCd(String prcsTypeCd) {
        return convert(prcsRepository.findAllByPrcsTypeCdOrderBySortOrdAsc(prcsTypeCd), PrcsResponse::from);
    }

    @Transactional
    public PrcsResponse addPrcs(PrcsAddRequest request) {
        Prcs prcs = new Prcs(request.prcsId(), request.prcsNm(), request.prcsTypeCd(), request.useYn(), request.sortOrd());
        return PrcsResponse.from(prcsRepository.save(prcs));
    }

    @Transactional
    public List<PrcsResponse> addPrcsList(PrcsAddListRequest request) {
        List<Prcs> prcsList = request.requests().stream()
                .map(item -> new Prcs(item.prcsId(), item.prcsNm(), item.prcsTypeCd(), item.useYn(), item.sortOrd()))
                .toList();

        return convert(prcsRepository.saveAll(prcsList), PrcsResponse::from);
    }

    @Transactional
    public PrcsResponse modifyPrcs(PrcsModifyRequest request) {
        Prcs prcs = getPrcsEntity(request.prcsId());
        prcs.replace(request.prcsNm(), request.prcsTypeCd(), request.useYn(), request.sortOrd());
        return PrcsResponse.from(prcs);
    }


    @Transactional
    public List<PrcsResponse> modifyPrcsList(PrcsModifyListRequest request) {
        List<PrcsModifyRequest> requests = request.requests();

        List<String> prcsIds = requests.stream()
                .map(PrcsModifyRequest::prcsId)
                .toList();

        List<Prcs> prcsList = prcsRepository.findAllById(prcsIds);

        if (prcsList.size() != prcsIds.size()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }

        Map<String, PrcsModifyRequest> requestByPrcsId = requests.stream()
                .collect(Collectors.toMap(PrcsModifyRequest::prcsId, Function.identity()));

        prcsList.forEach(prcs -> {
            PrcsModifyRequest item = requestByPrcsId.get(prcs.getPrcsId());
            prcs.replace(item.prcsNm(), item.prcsTypeCd(), item.useYn(), item.sortOrd());
        });

        return prcsList.stream()
                .map(PrcsResponse::from)
                .toList();
    }


    private <R> List<R> convert(List<Prcs> prcsList, Function<Prcs, R> mapper) {
        return prcsList.stream().map(mapper).toList();
    }

    private Prcs getPrcsEntity(String prcsId) {
        return prcsRepository.findById(prcsId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
