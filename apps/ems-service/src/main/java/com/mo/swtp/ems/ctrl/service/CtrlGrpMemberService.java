package com.mo.swtp.ems.ctrl.service;

import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

import com.mo.swtp.common.api.BusinessException;
import com.mo.swtp.common.api.CommonErrorCode;
import com.mo.swtp.ems.ctrl.domain.CtrlEqp;
import com.mo.swtp.ems.ctrl.domain.CtrlGrpTag;
import com.mo.swtp.ems.ctrl.domain.CtrlGrpWnp;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpMemberReplaceResponse;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpMemberResponse;
import com.mo.swtp.ems.ctrl.dto.CtrlGrpStolenMemberResponse;
import com.mo.swtp.ems.ctrl.repository.CtrlEqpRepository;
import com.mo.swtp.ems.ctrl.repository.CtrlGrpRepository;
import com.mo.swtp.ems.ctrl.repository.CtrlGrpTagRepository;
import com.mo.swtp.ems.ctrl.repository.CtrlGrpWnpRepository;
import com.mo.swtp.ems.support.EmsErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제어그룹 편성 3종의 컬렉션 전체 교체(replace-all).
 *
 * <p>세 자원(설비·수계통지점·태그)이 <b>같은 코드 경로</b>를 탄다. 스키마는 감사 컬럼이 비대칭이지만
 * ({@code ctrl_eqp_p}만 {@code mdf_*}가 없다) 그 비대칭이 서비스 계층까지 번지지 않게 한다 —
 * 코드 경로가 갈리면 한쪽만 고쳐지는 결함이 자란다(문서 03 「결정 4」).
 *
 * <p><b>저장 절차는 네 단계이고 순서가 전부 의미를 갖는다.</b>
 *
 * <pre>
 * ① 그룹 존재 확인 + 요청 안 중복 검사
 * ② stolen 조회        ← 삭제 전에 해야 "어느 그룹에서 왔는지"를 알 수 있다
 * ③ 삭제 두 갈래       ← 그룹 기준(빠지는 것) + ID 기준(뺏어오는 것의 감사를 새로 찍기 위해)
 * ④ INSERT             ← ③이 DB에 도달한 뒤여야 PK가 충돌하지 않는다
 * </pre>
 *
 * <p>④가 이 작업의 핵심 위험으로 지목됐던 자리다. Hibernate는 flush 시
 * {@code insertions → updates → deletions} 순으로 실행하므로, 원리상 삭제와 저장을 그냥 나란히 부르면
 * INSERT가 DELETE보다 먼저 나가 PK 위반이 난다. 그리고 편성 저장은 거의 항상
 * "대부분 그대로 두고 몇 개만 들고 나는" 형태라 <b>PK가 겹치는 것이 정상 경로</b>다.
 *
 * <p><b>그런데 실측해 보니 이 경로에서는 그 위험이 발현되지 않는다.</b> 벌크 DELETE를 파생 삭제
 * 메서드로 바꿔도 편성 테스트 14건이 전부 통과한다. assigned-ID라 {@code save()}가 {@code merge()}를
 * 타고 그 존재 확인 SELECT가 보류 중인 DELETE를 함께 flush시키기 때문으로 보인다 —
 * <b>추정이며 확인하지 않았다.</b> 02가 "결함"으로 지목했던 merge 경로가 여기서는 안전판 노릇을 하는 셈이다.
 * 벌크 DELETE를 쓰는 이유는 쿼리 수와 컨텍스트 상태의 단순함으로 좁혀 둔다(문서 03 「함정 기록」 1).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CtrlGrpMemberService {

    private final CtrlGrpRepository ctrlGrpRepository;
    private final CtrlEqpRepository ctrlEqpRepository;
    private final CtrlGrpWnpRepository ctrlGrpWnpRepository;
    private final CtrlGrpTagRepository ctrlGrpTagRepository;

    // ── 조회 ──────────────────────────────────────────────────────

    public List<CtrlGrpMemberResponse> getEqpMembers(String ctrlGrpId) {
        requireGroupExists(ctrlGrpId);
        return ctrlEqpRepository.findAllByCtrlGrpIdOrdered(ctrlGrpId).stream()
                .map(CtrlGrpMemberResponse::from).toList();
    }

    public List<CtrlGrpMemberResponse> getWnpMembers(String ctrlGrpId) {
        requireGroupExists(ctrlGrpId);
        return ctrlGrpWnpRepository.findAllByCtrlGrpIdOrdered(ctrlGrpId).stream()
                .map(CtrlGrpMemberResponse::from).toList();
    }

    public List<CtrlGrpMemberResponse> getTagMembers(String ctrlGrpId) {
        requireGroupExists(ctrlGrpId);
        return ctrlGrpTagRepository.findAllByCtrlGrpIdOrdered(ctrlGrpId).stream()
                .map(CtrlGrpMemberResponse::from).toList();
    }

    // ── 교체 ──────────────────────────────────────────────────────

    @Transactional
    public CtrlGrpMemberReplaceResponse replaceEqpMembers(String ctrlGrpId, List<String> eqpIds) {
        prepare(ctrlGrpId, eqpIds);

        // ② 삭제 전에 뺏어올 대상을 파악한다 — 지운 뒤에는 출처 그룹을 알 수 없다
        List<CtrlGrpStolenMemberResponse> stolen = ctrlEqpRepository.findAllById(eqpIds).stream()
                .filter(member -> !member.getCtrlGrpId().equals(ctrlGrpId))
                .map(member -> new CtrlGrpStolenMemberResponse(member.getEqpId(), member.getCtrlGrpId()))
                .toList();

        // ③ 두 갈래 삭제. ID 기준 쪽이 없으면 남의 그룹 행이 merge로 조용히 UPDATE되어
        //    rgstr_* 가 옛 그룹 편성 시각에 얼어붙는다(updatable=false라 예외도 없다)
        ctrlEqpRepository.deleteAllByCtrlGrpId(ctrlGrpId);
        if (!eqpIds.isEmpty()) {
            ctrlEqpRepository.deleteAllByEqpIdIn(eqpIds);
        }

        // ④ 요청 배열 순서로 1부터 sort_ord를 매겨 다시 넣는다
        List<CtrlEqp> saved = saveFlushing(ctrlEqpRepository,
                indexed(eqpIds, (eqpId, order) -> new CtrlEqp(ctrlGrpId, eqpId, order)));

        return new CtrlGrpMemberReplaceResponse(
                saved.stream().map(CtrlGrpMemberResponse::from).toList(), stolen);
    }

    @Transactional
    public CtrlGrpMemberReplaceResponse replaceWnpMembers(String ctrlGrpId, List<String> wnpIds) {
        prepare(ctrlGrpId, wnpIds);

        List<CtrlGrpStolenMemberResponse> stolen = ctrlGrpWnpRepository.findAllById(wnpIds).stream()
                .filter(member -> !member.getCtrlGrpId().equals(ctrlGrpId))
                .map(member -> new CtrlGrpStolenMemberResponse(member.getWnpId(), member.getCtrlGrpId()))
                .toList();

        ctrlGrpWnpRepository.deleteAllByCtrlGrpId(ctrlGrpId);
        if (!wnpIds.isEmpty()) {
            ctrlGrpWnpRepository.deleteAllByWnpIdIn(wnpIds);
        }

        List<CtrlGrpWnp> saved = saveFlushing(ctrlGrpWnpRepository,
                indexed(wnpIds, (wnpId, order) -> new CtrlGrpWnp(ctrlGrpId, wnpId, order)));

        return new CtrlGrpMemberReplaceResponse(
                saved.stream().map(CtrlGrpMemberResponse::from).toList(), stolen);
    }

    @Transactional
    public CtrlGrpMemberReplaceResponse replaceTagMembers(String ctrlGrpId, List<String> tagSns) {
        prepare(ctrlGrpId, tagSns);

        List<CtrlGrpStolenMemberResponse> stolen = ctrlGrpTagRepository.findAllById(tagSns).stream()
                .filter(member -> !member.getCtrlGrpId().equals(ctrlGrpId))
                .map(member -> new CtrlGrpStolenMemberResponse(member.getTagSn(), member.getCtrlGrpId()))
                .toList();

        ctrlGrpTagRepository.deleteAllByCtrlGrpId(ctrlGrpId);
        if (!tagSns.isEmpty()) {
            ctrlGrpTagRepository.deleteAllByTagSnIn(tagSns);
        }

        List<CtrlGrpTag> saved = saveFlushing(ctrlGrpTagRepository,
                indexed(tagSns, (tagSn, order) -> new CtrlGrpTag(ctrlGrpId, tagSn, order)));

        return new CtrlGrpMemberReplaceResponse(
                saved.stream().map(CtrlGrpMemberResponse::from).toList(), stolen);
    }

    // ── 보조 ──────────────────────────────────────────────────────

    /**
     * 저장하고 즉시 flush한다 — 두 가지가 여기 달려 있다.
     *
     * <p><b>감사 컬럼</b>은 flush 시점에 채워지므로 그 전에 응답을 만들면 {@code rgstrDttm}이 비어 나간다
     * (02 「함정 기록」 5와 같은 이유).
     *
     * <p><b>PK 위반의 번역</b> — 삭제 두 갈래는 읽고 나서 쓰는 구조라 원자적이지 않다.
     * 미편성 설비를 두 그룹이 동시에 가져가면 양쪽 삭제가 0행을 지우고(잠글 행이 없다) 둘 다 INSERT해,
     * 뒤엣것이 PK 위반으로 터진다. flush를 커밋까지 미루면 그 예외가 트랜잭션 밖에서 나
     * catch-all이 <b>409가 아니라 500</b>으로 응답한다. 02의 마스터 등록 경로가 같은 이유로 같은 형태다.
     */
    private <T> List<T> saveFlushing(JpaRepository<T, String> repository, List<T> members) {
        try {
            List<T> saved = repository.saveAll(members);
            repository.flush();
            return saved;
        }
        catch (DataIntegrityViolationException e) {
            throw new BusinessException(EmsErrorCode.DUPLICATE_ID, "편성 동시 저장 충돌");
        }
    }

    /** ① 그룹 존재 확인 + 요청 안 중복 검사 */
    private void prepare(String ctrlGrpId, List<String> memberIds) {
        requireGroupExists(ctrlGrpId);
        requireNoDuplicateWithin(memberIds);
    }

    /**
     * 그룹이 실재하는지만 본다 — <b>{@code use_yn}을 보지 않는다.</b>
     *
     * <p>단독키 PK 때문에 비활성 그룹이 설비를 인질로 잡는다. 1계열을 {@code 'N'}으로 내려도
     * 그 펌프는 여전히 1계열 소속이라 2계열에 편성할 수 없다. 비활성 그룹의 편성 저장을 막으면
     * 운영자가 빠져나갈 길이 없어진다(문서 03 「결정 3」).
     */
    private void requireGroupExists(String ctrlGrpId) {
        if (!ctrlGrpRepository.existsById(ctrlGrpId)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "제어그룹 없음: " + ctrlGrpId);
        }
    }

    /**
     * 요청 안의 식별자 중복 검사.
     *
     * <p>중복을 그대로 두면 PK가 같은 행을 두 번 INSERT해 제약 위반으로 500이 난다.
     * 또 정렬순서가 둘 중 어느 쪽으로 정해지는지도 요청만 봐서는 알 수 없다.
     */
    private void requireNoDuplicateWithin(Collection<String> memberIds) {
        if (memberIds.stream().distinct().count() != memberIds.size()) {
            throw new BusinessException(EmsErrorCode.DUPLICATE_ID, "요청 안에 편성 대상이 중복됨");
        }
    }

    /** 배열 순서를 1부터의 정렬순서로 바꿔 엔티티를 만든다 — 세 엔티티 타입이 공유한다 */
    private <T> List<T> indexed(List<String> memberIds, MemberFactory<T> factory) {
        return IntStream.range(0, memberIds.size())
                .mapToObj(index -> factory.create(memberIds.get(index), index + 1))
                .toList();
    }

    /** {@link #indexed}가 쓰는 생성 계약 — 대상ID와 정렬순서를 받아 편성 엔티티를 만든다 */
    @FunctionalInterface
    private interface MemberFactory<T> {

        T create(String memberId, int sortOrd);
    }
}
