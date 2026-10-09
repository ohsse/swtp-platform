package com.mo.swtp.ems.wnp.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.mo.swtp.common.api.BusinessException;
import com.mo.swtp.common.api.CommonErrorCode;
import com.mo.swtp.ems.support.EmsErrorCode;
import com.mo.swtp.ems.support.UseYn;
import com.mo.swtp.ems.wnp.domain.Wnp;
import com.mo.swtp.ems.wnp.dto.WnpAddListRequest;
import com.mo.swtp.ems.wnp.dto.WnpAddRequest;
import com.mo.swtp.ems.wnp.dto.WnpModifyItemRequest;
import com.mo.swtp.ems.wnp.dto.WnpModifyListRequest;
import com.mo.swtp.ems.wnp.dto.WnpModifyRequest;
import com.mo.swtp.ems.wnp.dto.WnpResponse;
import com.mo.swtp.ems.wnp.repository.WnpRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수계통지점 CRUD.
 *
 * <p>클래스에 {@code readOnly = true}가 걸려 있으므로 쓰기 메서드마다 {@code @Transactional}을 명시한다 —
 * 읽기 전용 트랜잭션은 FlushMode를 MANUAL로 만들어 빠뜨리면 INSERT가 조용히 사라진다.
 *
 * <p>쓰기 경로는 응답을 만들기 전에 flush한다. 감사 컬럼을 채우는 {@code @PreUpdate}가 flush 시점에
 * 발화하고, PK 위반도 이 메서드 안에서 잡혀야 409로 나갈 수 있다.
 *
 * <p>제어그룹 서비스를 참조하지 않는다. 두 도메인은 편성 명세(문서 03)를 통해서만 이어진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WnpService {

    private final WnpRepository wnpRepository;

    /** 목록 조회 — 비어 있어도 예외를 던지지 않는다(빈 배열 200) */
    public List<WnpResponse> getWnpList(UseYn useYn) {
        List<Wnp> wnps = useYn != null
                ? wnpRepository.findAllByUseYnOrdered(useYn)
                : wnpRepository.findAllOrdered();

        return wnps.stream().map(WnpResponse::from).toList();
    }

    /** 단건 조회 — 지목한 자원이 없는 것은 실제 오류이므로 404다 */
    public WnpResponse getWnp(String wnpId) {
        return WnpResponse.from(getEntity(wnpId));
    }

    @Transactional
    public WnpResponse addWnp(WnpAddRequest request) {
        requireAbsent(List.of(request.wnpId()));

        Wnp wnp = new Wnp(request.wnpId(), request.wnpNm(), request.useYn(), request.sortOrd());

        return WnpResponse.from(saveFlushing(List.of(wnp)).get(0));
    }

    @Transactional
    public List<WnpResponse> addWnpList(WnpAddListRequest request) {
        List<String> ids = request.requests().stream().map(WnpAddRequest::wnpId).toList();
        requireNoDuplicateWithin(ids);
        requireAbsent(ids);

        List<Wnp> wnps = request.requests().stream()
                .map(item -> new Wnp(item.wnpId(), item.wnpNm(), item.useYn(), item.sortOrd()))
                .toList();

        return saveFlushing(wnps).stream().map(WnpResponse::from).toList();
    }

    /** 단건 수정 — 대상은 경로가 정한다. 본문에는 식별자가 없다 */
    @Transactional
    public WnpResponse modifyWnp(String wnpId, WnpModifyRequest request) {
        Wnp wnp = getEntity(wnpId);
        wnp.replace(request.wnpNm(), request.useYn(), request.sortOrd());

        // flush 없이 응답을 만들면 mdf_dttm·mdf_id가 수정 "이전" 값으로 나간다 — @PreUpdate가 flush 시점에 발화한다
        wnpRepository.flush();

        return WnpResponse.from(wnp);
    }

    @Transactional
    public List<WnpResponse> modifyWnpList(WnpModifyListRequest request) {
        List<WnpModifyItemRequest> items = request.requests();
        List<String> ids = items.stream().map(WnpModifyItemRequest::wnpId).toList();
        requireNoDuplicateWithin(ids);

        List<Wnp> wnps = wnpRepository.findAllById(ids);
        // 하나라도 없으면 아무것도 바꾸지 않고 끊는다 — 변경 전에 검사하므로 롤백까지 가지 않는다
        if (wnps.size() != ids.size()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }

        Map<String, Wnp> entityById = wnps.stream()
                .collect(Collectors.toMap(Wnp::getWnpId, Function.identity()));

        items.forEach(item -> entityById.get(item.wnpId())
                .replace(item.wnpNm(), item.useYn(), item.sortOrd()));

        wnpRepository.flush();

        // 응답을 요청 순서로 돌려준다 — findAllById의 반환 순서는 DB가 정한다
        return items.stream()
                .map(item -> WnpResponse.from(entityById.get(item.wnpId())))
                .toList();
    }

    private Wnp getEntity(String wnpId) {
        return wnpRepository.findById(wnpId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "수계통지점 없음: " + wnpId));
    }

    /**
     * 저장하고 즉시 flush한다 — PK 위반을 이 메서드 안에서 잡기 위해서다.
     *
     * <p>{@link #requireAbsent}는 check-then-act라 원자적이지 않다. flush를 커밋까지 미루면
     * 경합으로 생긴 PK 위반이 트랜잭션 경계 밖에서 나고, catch-all 핸들러가 409가 아니라 500으로 응답한다.
     */
    private List<Wnp> saveFlushing(List<Wnp> wnps) {
        try {
            List<Wnp> saved = wnpRepository.saveAll(wnps);
            wnpRepository.flush();
            return saved;
        }
        catch (DataIntegrityViolationException e) {
            throw new BusinessException(EmsErrorCode.DUPLICATE_ID, "수계통지점ID 동시 등록 충돌");
        }
    }

    /**
     * 등록 전 존재 검사 — 없으면 등록이 조용한 수정이 된다.
     *
     * <p>PK가 assigned-ID이고 {@code Persistable} 구현이 없어 {@code save()}가 {@code merge()}를 타므로,
     * 기존 ID로 등록해도 예외가 나지 않고 UPDATE가 된다.
     */
    private void requireAbsent(List<String> wnpIds) {
        List<String> existing = wnpRepository.findAllById(wnpIds).stream()
                .map(Wnp::getWnpId)
                .toList();

        if (!existing.isEmpty()) {
            throw new BusinessException(EmsErrorCode.DUPLICATE_ID, "이미 존재하는 수계통지점ID: " + existing);
        }
    }

    /**
     * 요청 안의 식별자 중복 검사.
     *
     * <p>{@code Collectors.toMap}이 중복 키에 예외를 던져 500이 되는 것을 앞에서 막는다.
     * 등록 경로에서도 필요하다 — 한 요청에 같은 ID가 두 번 오면 DB에는 없으므로
     * {@link #requireAbsent}를 통과하고, 뒤 항목이 앞 항목을 덮어쓴 결과만 남는다.
     */
    private void requireNoDuplicateWithin(List<String> wnpIds) {
        if (wnpIds.stream().distinct().count() != wnpIds.size()) {
            throw new BusinessException(EmsErrorCode.DUPLICATE_ID, "요청 안에 수계통지점ID가 중복됨");
        }
    }
}
