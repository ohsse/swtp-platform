package com.mo.swtp.ems.ctrl.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.mo.swtp.common.api.BusinessException;
import com.mo.swtp.common.api.CommonErrorCode;
import com.mo.swtp.ems.ctrl.domain.CtrlGrp;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpAddListRequest;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpAddRequest;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpModifyItemRequest;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpModifyListRequest;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpModifyRequest;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpResponse;
import com.mo.swtp.ems.ctrl.repository.CtrlGrpRepository;
import com.mo.swtp.ems.support.EmsErrorCode;
import com.mo.swtp.ems.support.UseYn;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제어그룹 CRUD.
 *
 * <p>클래스에 {@code readOnly = true}가 걸려 있으므로 <b>쓰기 메서드마다 {@code @Transactional}을 명시한다.</b>
 * 읽기 전용 트랜잭션은 Hibernate의 FlushMode를 MANUAL로 만들어, 빠뜨리면 INSERT가 조용히 사라진다
 * (master-service가 같은 자리에서 겪은 문제다 — apps/master-service/docs/01 「트랜잭션 경계」).
 *
 * <p><b>쓰기 경로는 응답을 만들기 전에 반드시 flush한다.</b> 두 가지가 여기 달려 있다 —
 * 감사 컬럼을 채우는 {@code @PreUpdate}가 flush 시점에 발화한다는 것과,
 * PK 위반이 이 메서드 안에서 잡혀야 409로 나갈 수 있다는 것이다. 자세한 이유는 각 메서드 주석에 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CtrlGrpService {

    private final CtrlGrpRepository ctrlGrpRepository;

    /**
     * 목록 조회.
     *
     * <p><b>비어 있어도 예외를 던지지 않는다.</b> 신규 정수장은 제어그룹이 0건인 채로 출발하므로
     * 빈 결과를 404로 만들면 관리 화면을 처음 여는 순간 오류가 된다.
     * 프론트가 "아직 등록된 것이 없음"과 "조회가 실패함"을 구분할 수 없게 되는 쪽이 더 비싸다.
     */
    public List<CtrlGrpResponse> getCtrlGrpList(UseYn useYn) {
        List<CtrlGrp> ctrlGrps = useYn != null
                ? ctrlGrpRepository.findAllByUseYnOrdered(useYn)
                : ctrlGrpRepository.findAllOrdered();

        return ctrlGrps.stream().map(CtrlGrpResponse::from).toList();
    }

    /** 단건 조회 — 지목한 자원이 없는 것은 실제 오류이므로 404다 */
    public CtrlGrpResponse getCtrlGrp(String ctrlGrpId) {
        return CtrlGrpResponse.from(getEntity(ctrlGrpId));
    }

    @Transactional
    public CtrlGrpResponse addCtrlGrp(CtrlGrpAddRequest request) {
        requireAbsent(List.of(request.ctrlGrpId()));

        CtrlGrp ctrlGrp = new CtrlGrp(
                request.ctrlGrpId(), request.ctrlGrpNm(), request.useYn(), request.sortOrd());

        return CtrlGrpResponse.from(saveFlushing(List.of(ctrlGrp)).get(0));
    }

    @Transactional
    public List<CtrlGrpResponse> addCtrlGrpList(CtrlGrpAddListRequest request) {
        List<String> ids = request.requests().stream().map(CtrlGrpAddRequest::ctrlGrpId).toList();
        requireNoDuplicateWithin(ids);
        requireAbsent(ids);

        List<CtrlGrp> ctrlGrps = request.requests().stream()
                .map(item -> new CtrlGrp(item.ctrlGrpId(), item.ctrlGrpNm(), item.useYn(), item.sortOrd()))
                .toList();

        return saveFlushing(ctrlGrps).stream().map(CtrlGrpResponse::from).toList();
    }

    /** 단건 수정 — 대상은 경로가 정한다. 본문에는 식별자가 없다 */
    @Transactional
    public CtrlGrpResponse modifyCtrlGrp(String ctrlGrpId, CtrlGrpModifyRequest request) {
        CtrlGrp ctrlGrp = getEntity(ctrlGrpId);
        ctrlGrp.replace(request.ctrlGrpNm(), request.useYn(), request.sortOrd());

        // flush 없이 응답을 만들면 mdf_dttm·mdf_id가 수정 "이전" 값으로 나간다 —
        // 그 둘을 채우는 AuditingEntityListener의 @PreUpdate가 flush 시점에 발화하기 때문이다.
        // DB 행은 커밋 때 제대로 갱신되므로 응답만 거짓이 되어 더 찾기 어렵다.
        ctrlGrpRepository.flush();

        return CtrlGrpResponse.from(ctrlGrp);
    }

    @Transactional
    public List<CtrlGrpResponse> modifyCtrlGrpList(CtrlGrpModifyListRequest request) {
        List<CtrlGrpModifyItemRequest> items = request.requests();
        List<String> ids = items.stream().map(CtrlGrpModifyItemRequest::ctrlGrpId).toList();
        requireNoDuplicateWithin(ids);

        List<CtrlGrp> ctrlGrps = ctrlGrpRepository.findAllById(ids);
        // 하나라도 없으면 아무것도 바꾸지 않고 끊는다 — 일부만 반영된 상태가 남으면
        // 재요청이 무엇을 다시 보내야 하는지 알 수 없다. 변경 전에 검사하므로 롤백까지 가지 않는다.
        if (ctrlGrps.size() != ids.size()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }

        Map<String, CtrlGrp> entityById = ctrlGrps.stream()
                .collect(Collectors.toMap(CtrlGrp::getCtrlGrpId, Function.identity()));

        items.forEach(item -> entityById.get(item.ctrlGrpId())
                .replace(item.ctrlGrpNm(), item.useYn(), item.sortOrd()));

        // 단건 수정과 같은 이유 — 감사 컬럼을 채운 뒤에 응답을 만든다
        ctrlGrpRepository.flush();

        // 응답을 요청 순서로 돌려준다. findAllById의 반환 순서는 DB가 정하므로,
        // 그 순서를 그대로 쓰면 요청과 응답을 위치로 짝지은 클라이언트가 값을 잘못 붙인다.
        return items.stream()
                .map(item -> CtrlGrpResponse.from(entityById.get(item.ctrlGrpId())))
                .toList();
    }

    private CtrlGrp getEntity(String ctrlGrpId) {
        return ctrlGrpRepository.findById(ctrlGrpId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "제어그룹 없음: " + ctrlGrpId));
    }

    /**
     * 저장하고 즉시 flush한다 — PK 위반을 이 메서드 안에서 잡기 위해서다.
     *
     * <p>{@link #requireAbsent}는 읽고 나서 쓰는(check-then-act) 구조라 원자적이지 않다.
     * 같은 ID로 두 요청이 동시에 오면 둘 다 존재검사를 통과하고 뒤엣것이 PK 위반으로 터지는데,
     * flush를 커밋까지 미루면 그 예외가 트랜잭션 경계 밖에서 나므로
     * {@code GlobalExceptionHandler}의 catch-all이 <b>409가 아니라 500</b>으로 응답한다.
     * 설계 전체가 409를 약속하고 있으므로 그 약속을 경합 상황에서도 지킨다.
     */
    private List<CtrlGrp> saveFlushing(List<CtrlGrp> ctrlGrps) {
        try {
            List<CtrlGrp> saved = ctrlGrpRepository.saveAll(ctrlGrps);
            ctrlGrpRepository.flush();
            return saved;
        }
        catch (DataIntegrityViolationException e) {
            throw new BusinessException(EmsErrorCode.DUPLICATE_ID, "제어그룹ID 동시 등록 충돌");
        }
    }

    /**
     * 등록 전 존재 검사 — <b>없으면 등록이 조용한 수정이 된다.</b>
     *
     * <p>PK가 assigned-ID이고 {@code Persistable} 구현이 없어 {@code save()}가 {@code merge()}를 타므로,
     * 기존 ID로 등록해도 예외가 나지 않고 UPDATE가 된다. 그러면 {@code rgstr_dttm}은
     * {@code updatable = false}라 옛 값에 멈춘 채 내용만 바뀐 행이 남는다.
     *
     * <p>단건도 {@code findAllById}로 검사한다 — 단건과 일괄이 같은 경로를 타야
     * 한쪽만 고쳐지는 일이 생기지 않는다.
     */
    private void requireAbsent(List<String> ctrlGrpIds) {
        List<String> existing = ctrlGrpRepository.findAllById(ctrlGrpIds).stream()
                .map(CtrlGrp::getCtrlGrpId)
                .toList();

        if (!existing.isEmpty()) {
            throw new BusinessException(EmsErrorCode.DUPLICATE_ID, "이미 존재하는 제어그룹ID: " + existing);
        }
    }

    /**
     * 요청 안의 식별자 중복 검사.
     *
     * <p>{@code Collectors.toMap}이 중복 키에 {@code IllegalStateException}을 던져 500이 되는 것을
     * 앞에서 막는다. 등록 경로에서도 필요하다 — 한 요청에 같은 ID가 두 번 오면 DB에는 없으므로
     * {@link #requireAbsent}를 통과해 버리고, 뒤 항목이 앞 항목을 덮어쓴 결과만 남는다.
     */
    private void requireNoDuplicateWithin(List<String> ctrlGrpIds) {
        if (ctrlGrpIds.stream().distinct().count() != ctrlGrpIds.size()) {
            throw new BusinessException(EmsErrorCode.DUPLICATE_ID, "요청 안에 제어그룹ID가 중복됨");
        }
    }
}
