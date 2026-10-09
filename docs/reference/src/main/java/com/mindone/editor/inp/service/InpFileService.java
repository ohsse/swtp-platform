package com.mindone.editor.inp.service;

import com.mindone.editor.common.domain.YesOrNo;
import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.common.exception.error.CommonErrorCode;
import com.mindone.editor.inp.domain.InpFile;
import com.mindone.editor.inp.domain.InpFileRevision;
import com.mindone.editor.inp.domain.RevisionWorkType;
import com.mindone.editor.inp.dto.InpFileDownload;
import com.mindone.editor.inp.dto.InpFileResponse;
import com.mindone.editor.inp.dto.InpFileRevisionRef;
import com.mindone.editor.inp.dto.InpFileRevisionResponse;
import com.mindone.editor.inp.exception.InpFileErrorCode;
import com.mindone.editor.inp.repository.InpFileRepository;
import com.mindone.editor.inp.repository.InpFileRevisionRepository;
import com.mindone.editor.inp.storage.InpFileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * INP 파일 업로드/관리 서비스 — 리비전(이력) 기반.
 *
 * <p>파일은 마스터({@link InpFile}) 1건 + 리비전({@link InpFileRevision}) N건으로 관리된다. 마스터는
 * "현재 적용 리비전"만 가리키고, 조회/다운로드는 항상 그 리비전을 본다.</p>
 *
 * <ul>
 *   <li>업로드 → 마스터 + rev0(ORIGIN)</li>
 *   <li>새로쓰기(save-as) → 새 마스터 + rev0(EDIT)</li>
 *   <li>덮어쓰기 → 새 리비전(EDIT, rev = max+1) + 포인터 이동</li>
 *   <li>롤백 → 기존 리비전으로 포인터만 이동(파일/행 추가 없음)</li>
 * </ul>
 *
 * <p>OPTIMIZE(파이썬 최적화) 리비전은 파이썬 모듈이 물리 파일·이력 행·포인터를 공유 DB 에 직접 기록한다
 * (이 서비스는 OPTIMIZE 쓰기를 수행하지 않으며, 조회/롤백/삭제 시 함께 다룬다).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpFileService {

    /** 허용 확장자 (INP). */
    private static final String ALLOWED_EXTENSION = "inp";

    private final InpFileRepository inpFileRepository;
    private final InpFileRevisionRepository inpFileRevisionRepository;
    private final InpFileStorage inpFileStorage;

    /**
     * INP 파일을 업로드한다(마스터 + rev0 ORIGIN).
     *
     * <p>메타데이터(마스터·리비전)를 먼저 저장한 뒤 물리 파일을 기록한다. 물리 저장이 실패하면
     * 예외가 전파되어 트랜잭션이 롤백되므로 메타도 함께 취소된다.</p>
     *
     * @param file     업로드된 INP 파일
     * @param fileName 사용자가 입력한 원본 파일명(표시용). 비어 있으면 업로드 파일명으로 대체한다.
     * @return 등록된 INP 파일 메타데이터(rev0)
     */
    @Transactional
    public InpFileResponse upload(MultipartFile file, String fileName) {
        // 1. 빈 파일 검증
        if (file == null || file.isEmpty()) {
            throw new RestApiException(InpFileErrorCode.EMPTY_FILE);
        }

        // 2. 확장자 검증 (업로드 파일이 INP 인지)
        String uploadName = file.getOriginalFilename();
        String extension = extractExtension(uploadName);
        if (!ALLOWED_EXTENSION.equals(extension)) {
            throw new RestApiException(InpFileErrorCode.INVALID_FILE_EXTENSION);
        }

        // 3. 원본 파일명 결정 (비어 있으면 업로드 파일명으로 대체) + NFC 정규화
        //    macOS 업로드 파일명은 NFD(자모 분리) 라서 정규화하지 않으면 다운로드 시 'ㄱㅣㅁ...' 으로 깨져 보인다.
        String orgnlFileNm = normalizeFileName((fileName == null || fileName.isBlank()) ? uploadName : fileName);

        // 4. 마스터 + rev0(ORIGIN) 메타 저장
        InpFile master = inpFileRepository.saveAndFlush(InpFile.create(orgnlFileNm, extension));
        InpFileRevision rev0 = createRevision(master, 0, file.getSize(), RevisionWorkType.ORIGIN);

        // 5. 물리 파일 저장 (실패 시 예외 → 트랜잭션 롤백)
        inpFileStorage.store(file, rev0.getStorFileNm());

        return InpFileResponse.from(master, rev0);
    }

    /**
     * 서버가 생성한 INP 바이트를 새 INP 파일로 저장한다("다른 이름으로 저장", rev0 EDIT).
     *
     * <p>업로드와 동일한 라이프사이클(새 마스터 + rev0 + 새 물리 파일)이지만, 내용은 업로드 원본이 아니라
     * 편집 결과이므로 작업 구분은 {@link RevisionWorkType#EDIT} 다. 원본은 그대로 보존된다. 파일명에
     * {@code .inp} 확장자가 없으면 자동으로 붙인다.</p>
     *
     * @param content     저장할 INP 바이트(편집 결과를 직렬화한 것)
     * @param orgnlFileNm 사용자가 지정한 새 파일명(표시용)
     * @return 등록된 INP 파일 메타데이터(새 ID, rev0)
     * @throws RestApiException 바이트가 비었으면 {@link InpFileErrorCode#EMPTY_FILE},
     *                          파일명이 비었으면 {@link CommonErrorCode#INVALID_PARAMETER}
     */
    @Transactional
    public InpFileResponse saveBytes(byte[] content, String orgnlFileNm) {
        // 1. 빈 내용 검증
        if (content == null || content.length == 0) {
            throw new RestApiException(InpFileErrorCode.EMPTY_FILE);
        }

        // 2. 파일명 검증/정규화 (.inp 확장자 보장)
        if (orgnlFileNm == null || orgnlFileNm.isBlank()) {
            throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
        }
        String normalizedName = ensureInpExtension(normalizeFileName(orgnlFileNm.trim()));

        // 3. 새 마스터 + rev0(EDIT) 메타 저장
        InpFile master = inpFileRepository.saveAndFlush(InpFile.create(normalizedName, ALLOWED_EXTENSION));
        InpFileRevision rev0 = createRevision(master, 0, content.length, RevisionWorkType.ORIGIN);

        // 4. 물리 파일 저장 (실패 시 예외 → 트랜잭션 롤백)
        inpFileStorage.store(content, rev0.getStorFileNm());

        return InpFileResponse.from(master, rev0);
    }

    /**
     * 기존 INP 파일을 편집 결과로 덮어쓴다("원본 저장", 새 리비전 EDIT).
     *
     * <p>같은 파일을 in-place 로 덮어쓰지 않고, <b>새 리비전 파일</b>을 만들고 마스터 포인터를 그 리비전으로
     * 옮긴다(이력 보존). 다음 리비전 번호는 {@code max(rev_no)+1}. 메타(리비전 행 + 포인터)를 먼저 저장한 뒤
     * 물리 파일을 쓰므로, 물리 실패 시 트랜잭션 롤백으로 메타도 취소되고 스트레이 파일도 남지 않는다.</p>
     *
     * @param inpFileId 덮어쓸 INP 파일 ID
     * @param content   새 INP 바이트(편집 결과를 직렬화한 것)
     * @return 갱신된 INP 파일 메타데이터(ID 동일, 리비전 증가)
     * @throws RestApiException 바이트가 비었으면 {@link InpFileErrorCode#EMPTY_FILE},
     *                          대상이 없으면 {@link InpFileErrorCode#FILE_NOT_FOUND}
     */
    @Transactional
    public InpFileResponse overwriteBytes(String inpFileId, byte[] content) {
        // 1. 빈 내용 검증
        if (content == null || content.length == 0) {
            throw new RestApiException(InpFileErrorCode.EMPTY_FILE);
        }

        // 2. 대상 마스터 조회 (없으면 예외)
        InpFile master = requireMaster(inpFileId);

        // 3. 새 리비전(EDIT) 메타 저장 + 포인터 이동
        int nextRev = nextRevNo(inpFileId);
        InpFileRevision rev = createRevision(master, nextRev, content.length, RevisionWorkType.EDIT);
        master.applyRevision(nextRev);

        // 4. 새 리비전 물리 파일 저장 (실패 시 예외 → 트랜잭션 롤백)
        inpFileStorage.store(content, rev.getStorFileNm());

        return InpFileResponse.from(master, rev);
    }

    /**
     * INP 파일 목록을 등록일시 내림차순(최신순)으로 조회한다.
     *
     * <p>마스터의 현재 적용 리비전 번호({@code curr_rev_no})와 일치하는 리비전 이력을 단일 조인 쿼리로
     * 함께 가져온다(N+1 회피). 저장 파일명/크기/작업구분(work_type)은 현재 적용 리비전 기준 값이다.</p>
     *
     * @return INP 파일 메타데이터 목록 (없으면 빈 목록)
     */
    @Transactional(readOnly = true)
    public List<InpFileResponse> getList() {
        return inpFileRepository.findAllWithCurrentRevision()
                .stream()
                .map(row -> InpFileResponse.from((InpFile) row[0], (InpFileRevision) row[1]))
                .toList();
    }

    /**
     * 단일 INP 파일(<b>현재 적용 리비전</b>) 다운로드 대상을 조회한다.
     *
     * <p>마스터의 현재 적용 리비전 번호({@code curr_rev_no})와 일치하는 리비전 파일을 단일 조인 쿼리로 찾는다.</p>
     *
     * @param inpFileId INP 파일 ID
     * @return 다운로드 대상(리소스 + 원본 파일명, 현재 적용 리비전 기준)
     * @throws RestApiException 마스터가 없으면 {@link InpFileErrorCode#FILE_NOT_FOUND},
     *                          현재 리비전이 없으면 {@link InpFileErrorCode#REVISION_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public InpFileDownload download(String inpFileId) {
        // PK 조건의 LEFT JOIN 이라 결과는 0~1건
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
        return toDownload(current.getStorFileNm(), master.getOrgnlFileNm(), current.getFileSz());
    }

    /**
     * 특정 리비전의 다운로드 대상을 조회한다. (다운로드 파일명에 리비전 접미사를 붙여 구분)
     *
     * @param inpFileId INP 파일 ID
     * @param revNo     리비전 번호
     * @return 다운로드 대상
     * @throws RestApiException 마스터가 없으면 {@link InpFileErrorCode#FILE_NOT_FOUND},
     *                          리비전이 없으면 {@link InpFileErrorCode#REVISION_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public InpFileDownload downloadRevision(String inpFileId, int revNo) {
        InpFile master = requireMaster(inpFileId);
        InpFileRevision rev = requireRevision(inpFileId, revNo);
        String downloadName = revisionDownloadName(master.getOrgnlFileNm(), revNo);
        return toDownload(rev.getStorFileNm(), downloadName, rev.getFileSz());
    }

    /**
     * 여러 INP 파일(각자의 <b>현재 적용 리비전</b>) 다운로드 대상을 조회한다. (요청 순서 유지, 중복 ID 제거)
     *
     * <p>마스터의 현재 적용 리비전 번호({@code curr_rev_no})와 일치하는 리비전 파일을 단일 조인 쿼리로 한 번에
     * 가져온다(N+1 회피).</p>
     *
     * @param inpFileIds 다운로드할 INP 파일 ID 목록
     * @return 다운로드 대상 목록(현재 적용 리비전 기준)
     * @throws RestApiException 목록이 비었으면 {@link CommonErrorCode#INVALID_PARAMETER},
     *                          존재하지 않는 ID 가 있으면 {@link InpFileErrorCode#FILE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public List<InpFileDownload> downloadTargets(List<String> inpFileIds) {
        if (inpFileIds == null || inpFileIds.isEmpty()) {
            throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
        }
        // 중복 ID 제거(요청 순서 유지)
        List<String> distinctIds = inpFileIds.stream().distinct().toList();
        // [마스터, 현재 적용 리비전] 쌍을 단일 조인으로 조회 후 ID 로 인덱싱
        Map<String, Object[]> byId = inpFileRepository.findAllWithCurrentRevisionByIdIn(distinctIds).stream()
                .collect(Collectors.toMap(row -> ((InpFile) row[0]).getInpFileId(), Function.identity()));

        List<InpFileDownload> targets = new ArrayList<>(distinctIds.size());
        for (String id : distinctIds) {
            Object[] row = byId.get(id);
            if (row == null) { // 마스터 미존재(또는 현재 리비전 결손)
                throw new RestApiException(InpFileErrorCode.FILE_NOT_FOUND);
            }
            InpFile master = (InpFile) row[0];
            InpFileRevision current = (InpFileRevision) row[1];
            targets.add(toDownload(current.getStorFileNm(), master.getOrgnlFileNm(), current.getFileSz()));
        }
        return targets;
    }

    /**
     * 여러 INP 파일의 <b>특정 리비전</b> 다운로드 대상을 조회한다(특정 리비전 다중 ZIP 다운로드용).
     *
     * <p>현재 적용 리비전이 아니라 각 항목이 지정한 리비전 번호의 파일을 대상으로 한다. 같은 파일의 서로 다른
     * 리비전이 함께 와도 표시명에 {@code _r{revNo}} 접미사가 붙어 ZIP 엔트리명이 충돌하지 않는다. 요청 순서를
     * 유지하며 동일한 (파일ID, 리비전) 쌍은 중복 제거한다.</p>
     *
     * @param revisions 다운로드할 (INP 파일 ID + 리비전 번호) 목록
     * @return 다운로드 대상 목록(지정 리비전 기준, 표시명은 리비전 접미사 포함)
     * @throws RestApiException 목록이 비었으면 {@link CommonErrorCode#INVALID_PARAMETER},
     *                          마스터가 없으면 {@link InpFileErrorCode#FILE_NOT_FOUND},
     *                          리비전이 없으면 {@link InpFileErrorCode#REVISION_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public List<InpFileDownload> downloadRevisionTargets(List<InpFileRevisionRef> revisions) {
        if (revisions == null || revisions.isEmpty()) {
            throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
        }
        // 동일 (파일ID, 리비전) 쌍 중복 제거(요청 순서 유지)
        List<InpFileRevisionRef> distinctRefs = revisions.stream().distinct().toList();

        List<InpFileDownload> targets = new ArrayList<>(distinctRefs.size());
        for (InpFileRevisionRef ref : distinctRefs) {
            InpFile master = requireMaster(ref.inpFileId());
            InpFileRevision rev = requireRevision(ref.inpFileId(), ref.revNo());
            String downloadName = revisionDownloadName(master.getOrgnlFileNm(), ref.revNo());
            targets.add(toDownload(rev.getStorFileNm(), downloadName, rev.getFileSz()));
        }
        return targets;
    }

    /**
     * 특정 INP 파일의 리비전(이력) 목록을 리비전 번호 내림차순(최신 우선)으로 조회한다.
     *
     * @param inpFileId INP 파일 ID
     * @return 리비전 목록
     * @throws RestApiException 마스터가 없으면 {@link InpFileErrorCode#FILE_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public List<InpFileRevisionResponse> listRevisions(String inpFileId) {
        InpFile master = requireMaster(inpFileId);
        String orgnlFileNm = master.getOrgnlFileNm();
        int currRevNo = master.getCurrRevNo();
        return inpFileRevisionRepository.findByInpFileIdOrderByRevNoDesc(inpFileId)
                .stream()
                // 다운로드 파일명은 특정 리비전 다운로드와 동일한 명명 규칙(_r{revNo})으로 만들어 함께 내려준다.
                .map(rev -> InpFileRevisionResponse.from(
                        rev, currRevNo, revisionDownloadName(orgnlFileNm, rev.getRevNo())))
                .toList();
    }

    /**
     * 현재 적용 리비전을 기존 리비전으로 되돌린다(롤백).
     *
     * <p>새 리비전이나 물리 파일을 만들지 않고, 마스터의 {@code curr_rev_no} 포인터만 대상 리비전으로 옮긴다.
     * 이후 덮어쓰기 시 다음 번호는 여전히 {@code max(rev_no)+1} 로 채번되므로, 롤백으로 건너뛴 중간 리비전도
     * 이력에 그대로 남아 열람 가능하다.</p>
     *
     * @param inpFileId    INP 파일 ID
     * @param targetRevNo  되돌릴 대상 리비전 번호
     * @return 갱신된 INP 파일 메타데이터(현재 리비전 = 대상)
     * @throws RestApiException 마스터가 없으면 {@link InpFileErrorCode#FILE_NOT_FOUND},
     *                          대상 리비전이 없으면 {@link InpFileErrorCode#REVISION_NOT_FOUND}
     */
    @Transactional
    public InpFileResponse rollback(String inpFileId, int targetRevNo) {
        InpFile master = requireMaster(inpFileId);
        InpFileRevision target = requireRevision(inpFileId, targetRevNo);
        int prevRevNo = master.getCurrRevNo();
        master.applyRevision(targetRevNo);
        log.info("INP 리비전 롤백: inpFileId={}, {} → {}", inpFileId, prevRevNo, targetRevNo);
        return InpFileResponse.from(master, target);
    }

    /**
     * INP 파일의 모니터링 여부를 변경한다.
     *
     * <p>마스터의 {@code mntr_yn} 플래그만 변경한다(리비전/물리 파일과 무관). 영속 엔티티의 변경 감지로
     * UPDATE 되며, 응답은 현재 적용 리비전 기준으로 구성한다.</p>
     *
     * @param inpFileId    INP 파일 ID
     * @param monitoringYn 모니터링 대상 여부(Y/N)
     * @return 갱신된 INP 파일 메타데이터
     * @throws RestApiException 마스터가 없으면 {@link InpFileErrorCode#FILE_NOT_FOUND},
     *                          현재 리비전이 없으면 {@link InpFileErrorCode#REVISION_NOT_FOUND}
     */
    @Transactional
    public InpFileResponse changeMonitoring(String inpFileId, YesOrNo monitoringYn) {
        InpFile master = requireMaster(inpFileId);
        InpFileRevision current = requireRevision(inpFileId, master.getCurrRevNo());
        master.changeMonitoring(monitoringYn);
        log.info("INP 모니터링 여부 변경: inpFileId={}, monitoringYn={}", inpFileId, monitoringYn);
        return InpFileResponse.from(master, current);
    }

    /**
     * 단일 INP 파일을 삭제한다. (리비전 이력 일괄 삭제 → 마스터 삭제 → 커밋 성공 시 모든 리비전 물리 파일 삭제)
     *
     * @param inpFileId 삭제할 INP 파일 ID
     * @throws RestApiException 대상이 없으면 {@link InpFileErrorCode#FILE_NOT_FOUND}
     */
    @Transactional
    public void delete(String inpFileId) {
        InpFile master = requireMaster(inpFileId);
        List<String> storFileNames = inpFileRevisionRepository.findByInpFileId(inpFileId).stream()
                .map(InpFileRevision::getStorFileNm)
                .toList();
        inpFileRevisionRepository.deleteByInpFileId(inpFileId);
        inpFileRepository.delete(master);
        deleteFilesAfterCommit(storFileNames);
    }

    /**
     * 여러 INP 파일을 삭제한다. (리비전 이력 + 마스터 일괄 삭제 → 커밋 성공 시 모든 리비전 물리 파일 삭제)
     *
     * <p>요청 ID 중 하나라도 존재하지 않으면 전체를 롤백하고 예외를 던진다(원자적 삭제).</p>
     *
     * @param inpFileIds 삭제할 INP 파일 ID 목록
     * @throws RestApiException 목록이 비었으면 {@link CommonErrorCode#INVALID_PARAMETER},
     *                          존재하지 않는 ID 가 있으면 {@link InpFileErrorCode#FILE_NOT_FOUND}
     */
    @Transactional
    public void deleteAll(List<String> inpFileIds) {
        if (inpFileIds == null || inpFileIds.isEmpty()) {
            throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
        }
        List<String> distinctIds = inpFileIds.stream().distinct().toList();
        Map<String, InpFile> byId = inpFileRepository.findAllById(distinctIds).stream()
                .collect(Collectors.toMap(InpFile::getInpFileId, Function.identity()));

        List<InpFile> masters = new ArrayList<>(distinctIds.size());
        List<String> storFileNames = new ArrayList<>();
        for (String id : distinctIds) {
            InpFile master = byId.get(id);
            if (master == null) {
                throw new RestApiException(InpFileErrorCode.FILE_NOT_FOUND);
            }
            masters.add(master);
            inpFileRevisionRepository.findByInpFileId(id)
                    .forEach(rev -> storFileNames.add(rev.getStorFileNm()));
            inpFileRevisionRepository.deleteByInpFileId(id);
        }
        inpFileRepository.deleteAll(masters);
        deleteFilesAfterCommit(storFileNames);
    }

    // ===== 내부 헬퍼 =====

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

    /** 다음 리비전 번호(= max+1, 리비전이 없으면 0). */
    private int nextRevNo(String inpFileId) {
        Integer max = inpFileRevisionRepository.findMaxRevNo(inpFileId);
        return (max == null) ? 0 : max + 1;
    }

    /** 리비전 이력 행을 저장하고 즉시 반영한다(저장 파일명은 권장 규약으로 생성). */
    private InpFileRevision createRevision(InpFile master, int revNo, long fileSz, RevisionWorkType workType) {
        String storFileNm = master.storFileNmForRev(revNo);
        InpFileRevision rev = InpFileRevision.create(master.getInpFileId(), revNo, storFileNm, fileSz, null, workType);
        return inpFileRevisionRepository.saveAndFlush(rev);
    }

    /** 저장 파일명/표시명/크기로 다운로드 대상을 구성한다(물리 리소스 로드 포함). */
    private InpFileDownload toDownload(String storFileNm, String displayName, long fileSz) {
        Resource resource = inpFileStorage.loadAsResource(storFileNm);
        // 정규화 이전(NFD)에 저장된 기존 데이터도 다운로드 시점에 NFC 로 맞춰 깨짐을 방지한다.
        return new InpFileDownload(resource, normalizeFileName(displayName), fileSz);
    }

    /**
     * 파일명을 유니코드 NFC(정준 결합) 로 정규화한다.
     *
     * <p>한글이 NFD(자모 분리)로 들어오면 {@code 김}(1코드포인트)이 {@code ㄱ+ㅣ+ㅁ}(3코드포인트)으로 보관돼
     * 다운로드 파일명이 깨져 보인다. NFC 로 결합해 표시·저장 일관성을 보장한다. null 은 그대로 통과시킨다.</p>
     */
    private String normalizeFileName(String fileName) {
        return fileName == null ? null : Normalizer.normalize(fileName, Normalizer.Form.NFC);
    }

    /** 특정 리비전 다운로드 표시명을 만든다(확장자 앞에 {@code _r{revNo}} 삽입). */
    private String revisionDownloadName(String orgnlFileNm, int revNo) {
        int dot = orgnlFileNm.lastIndexOf('.');
        if (dot < 0) {
            return orgnlFileNm + "_r" + revNo;
        }
        return orgnlFileNm.substring(0, dot) + "_r" + revNo + orgnlFileNm.substring(dot);
    }

    /**
     * 트랜잭션 커밋이 성공한 뒤에 물리 파일을 삭제하도록 등록한다.
     *
     * <p>커밋이 실패/롤백되면 파일 삭제는 수행되지 않아 데이터가 보존된다. 트랜잭션이
     * 없는 호출이면 즉시 삭제한다(안전망).</p>
     */
    private void deleteFilesAfterCommit(List<String> storFileNames) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storFileNames.forEach(inpFileStorage::delete);
                }
            });
        } else {
            storFileNames.forEach(inpFileStorage::delete);
        }
    }

    /** 파일명이 {@code .inp} 로 끝나지 않으면 확장자를 붙인다(대소문자 무시). */
    private String ensureInpExtension(String fileName) {
        return ALLOWED_EXTENSION.equals(extractExtension(fileName)) ? fileName : fileName + "." + ALLOWED_EXTENSION;
    }

    /** 파일명에서 소문자 확장자를 추출한다. 확장자가 없으면 빈 문자열을 반환한다. */
    private String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase();
    }
}
