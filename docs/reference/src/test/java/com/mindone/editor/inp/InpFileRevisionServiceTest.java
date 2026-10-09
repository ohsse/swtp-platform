package com.mindone.editor.inp;

import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.inp.domain.InpFile;
import com.mindone.editor.inp.domain.InpFileRevision;
import com.mindone.editor.inp.domain.RevisionWorkType;
import com.mindone.editor.inp.dto.InpFileDownload;
import com.mindone.editor.inp.dto.InpFileResponse;
import com.mindone.editor.inp.dto.InpFileRevisionRef;
import com.mindone.editor.inp.dto.InpFileRevisionResponse;
import com.mindone.editor.inp.repository.InpFileRepository;
import com.mindone.editor.inp.repository.InpFileRevisionRepository;
import com.mindone.editor.inp.service.InpFileService;
import com.mindone.editor.inp.storage.InpFileStorage;
import com.mindone.editor.storage.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 리비전(이력) 동작 단위 검증 — 업로드 rev0(ORIGIN), 롤백(포인터 이동), 목록, 채번 규칙.
 *
 * <p>DB 없이 리포지토리를 목으로 대체하고, 물리 저장만 임시 디렉터리에 수행한다.</p>
 */
class InpFileRevisionServiceTest {

    private static final Charset MS949 = Charset.forName("MS949");

    @TempDir
    Path storageRoot;

    private InpFileRepository repository;
    private InpFileRevisionRepository revisionRepository;
    private InpFileStorage storage;
    private InpFileService inpFileService;

    @BeforeEach
    void setUp() {
        storage = new InpFileStorage(new StorageProperties(storageRoot));
        repository = mock(InpFileRepository.class);
        revisionRepository = mock(InpFileRevisionRepository.class);
        when(repository.saveAndFlush(any(InpFile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(revisionRepository.saveAndFlush(any(InpFileRevision.class))).thenAnswer(inv -> inv.getArgument(0));
        inpFileService = new InpFileService(repository, revisionRepository, storage);
    }

    @Test
    @DisplayName("업로드: 마스터 + rev0(ORIGIN)이 생성된다")
    void uploadCreatesOriginRev0() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "원본.inp", null, "[END]\n".getBytes(MS949));

        InpFileResponse res = inpFileService.upload(file, "원본관망도.inp");

        assertThat(res.currRevNo()).isZero();
        assertThat(res.orgnlFileNm()).isEqualTo("원본관망도.inp");
        assertThat(res.storFileNm()).isEqualTo(res.inpFileId() + "_r0.inp");

        // 저장된 리비전 행의 작업 구분은 ORIGIN, 리비전 번호 0
        ArgumentCaptor<InpFileRevision> captor = ArgumentCaptor.forClass(InpFileRevision.class);
        verify(revisionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getWorkType()).isEqualTo(RevisionWorkType.ORIGIN);
        assertThat(captor.getValue().getRevNo()).isZero();

        assertThat(storageRoot.resolve("originals").resolve(res.storFileNm())).exists();
    }

    @Test
    @DisplayName("롤백: 새 리비전 없이 현재 포인터만 대상 리비전으로 이동한다")
    void rollbackMovesPointerWithoutNewRevision() {
        InpFile master = InpFile.create("a.inp", "inp");
        master.applyRevision(2); // 현재 rev2 라고 가정
        InpFileRevision rev1 = InpFileRevision.create(
                master.getInpFileId(), 1, master.storFileNmForRev(1), 10, null, RevisionWorkType.EDIT);
        when(repository.findById(master.getInpFileId())).thenReturn(Optional.of(master));
        when(revisionRepository.findByInpFileIdAndRevNo(master.getInpFileId(), 1)).thenReturn(Optional.of(rev1));

        InpFileResponse res = inpFileService.rollback(master.getInpFileId(), 1);

        assertThat(res.currRevNo()).isEqualTo(1);
        assertThat(master.getCurrRevNo()).isEqualTo(1);
        assertThat(res.storFileNm()).isEqualTo(rev1.getStorFileNm());
        // 롤백은 리비전 행을 새로 만들지 않는다
        verify(revisionRepository, never()).saveAndFlush(any(InpFileRevision.class));
    }

    @Test
    @DisplayName("롤백: 대상 리비전이 없으면 예외를 던진다")
    void rollbackToMissingRevisionThrows() {
        InpFile master = InpFile.create("a.inp", "inp");
        when(repository.findById(master.getInpFileId())).thenReturn(Optional.of(master));
        when(revisionRepository.findByInpFileIdAndRevNo(master.getInpFileId(), 9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inpFileService.rollback(master.getInpFileId(), 9))
                .isInstanceOf(RestApiException.class);
    }

    @Test
    @DisplayName("채번: 롤백 이후 덮어쓰면 다음 리비전은 max+1(현재+1 아님)이다")
    void nextRevisionUsesMaxPlusOneAfterRollback() {
        InpFile master = InpFile.create("a.inp", "inp");
        master.applyRevision(1); // 롤백으로 현재 rev1
        when(repository.findById(master.getInpFileId())).thenReturn(Optional.of(master));
        when(revisionRepository.findMaxRevNo(master.getInpFileId())).thenReturn(2); // 이력 최대는 rev2

        InpFileResponse res = inpFileService.overwriteBytes(master.getInpFileId(), "[END]\n".getBytes(MS949));

        assertThat(res.currRevNo()).isEqualTo(3); // max(2)+1
        assertThat(master.getCurrRevNo()).isEqualTo(3);
        assertThat(res.storFileNm()).isEqualTo(master.storFileNmForRev(3));
        assertThat(storageRoot.resolve("originals").resolve(res.storFileNm())).exists();
    }

    @Test
    @DisplayName("파일 목록: 현재 적용 리비전과 조인해 저장 파일명/크기/작업구분을 함께 반환한다")
    void getListJoinsCurrentRevision() {
        InpFile master = InpFile.create("관망도.inp", "inp");
        master.applyRevision(2); // 현재 적용 리비전 = rev2(EDIT)
        InpFileRevision currentRev = InpFileRevision.create(
                master.getInpFileId(), 2, master.storFileNmForRev(2), 42, null, RevisionWorkType.EDIT);
        // 리포지토리는 [마스터, 현재 적용 리비전] 쌍을 조인해 반환한다.
        when(repository.findAllWithCurrentRevision())
                .thenReturn(List.<Object[]>of(new Object[]{master, currentRev}));

        List<InpFileResponse> res = inpFileService.getList();

        assertThat(res).hasSize(1);
        InpFileResponse item = res.get(0);
        assertThat(item.inpFileId()).isEqualTo(master.getInpFileId());
        assertThat(item.currRevNo()).isEqualTo(2);
        assertThat(item.storFileNm()).isEqualTo(currentRev.getStorFileNm());
        assertThat(item.fileSz()).isEqualTo(42);
        // 이력 테이블의 work_type 이 함께 조회된다.
        assertThat(item.workType()).isEqualTo(RevisionWorkType.EDIT);
    }

    @Test
    @DisplayName("단일 다운로드: 마스터의 현재 적용 리비전 파일을 조인으로 찾아 내려준다")
    void downloadResolvesCurrentRevisionViaJoin() {
        InpFile master = InpFile.create("관망도.inp", "inp");
        master.applyRevision(2); // 현재 적용 리비전 = rev2
        String id = master.getInpFileId();
        byte[] bytes = "[END]\n".getBytes(MS949);
        InpFileRevision current = InpFileRevision.create(
                id, 2, master.storFileNmForRev(2), bytes.length, null, RevisionWorkType.EDIT);
        storage.store(bytes, current.getStorFileNm()); // 물리 파일 준비
        when(repository.findWithCurrentRevisionById(id))
                .thenReturn(List.<Object[]>of(new Object[]{master, current}));

        InpFileDownload target = inpFileService.download(id);

        // 다운로드 표시명은 원본 파일명, 내용/크기는 현재 적용 리비전(rev2) 기준
        assertThat(target.orgnlFileNm()).isEqualTo("관망도.inp");
        assertThat(target.fileSz()).isEqualTo(bytes.length);
        assertThat(target.resource().getFilename()).isEqualTo(current.getStorFileNm());
    }

    @Test
    @DisplayName("다중 다운로드: 각 파일의 현재 적용 리비전을 조인으로 한 번에 찾아 요청 순서로 내려준다")
    void downloadTargetsResolveCurrentRevisionViaJoin() {
        InpFile a = InpFile.create("a.inp", "inp");
        a.applyRevision(1);
        InpFile b = InpFile.create("b.inp", "inp");
        b.applyRevision(0);
        InpFileRevision aRev = InpFileRevision.create(
                a.getInpFileId(), 1, a.storFileNmForRev(1), 5, null, RevisionWorkType.EDIT);
        InpFileRevision bRev = InpFileRevision.create(
                b.getInpFileId(), 0, b.storFileNmForRev(0), 7, null, RevisionWorkType.ORIGIN);
        storage.store("aaaaa".getBytes(MS949), aRev.getStorFileNm());
        storage.store("bbbbbbb".getBytes(MS949), bRev.getStorFileNm());
        when(repository.findAllWithCurrentRevisionByIdIn(List.of(a.getInpFileId(), b.getInpFileId())))
                .thenReturn(List.of(new Object[]{b, bRev}, new Object[]{a, aRev})); // 조회 순서가 뒤섞여 와도

        List<InpFileDownload> targets = inpFileService.downloadTargets(List.of(a.getInpFileId(), b.getInpFileId()));

        // 요청 순서(a, b)대로 정렬되어 내려간다
        assertThat(targets).extracting(InpFileDownload::orgnlFileNm).containsExactly("a.inp", "b.inp");
        assertThat(targets).extracting(InpFileDownload::fileSz).containsExactly(5L, 7L);
    }

    @Test
    @DisplayName("특정 리비전 다중 다운로드: 표시명에 리비전 접미사가 붙고 동일 (파일,리비전)은 중복 제거된다")
    void downloadRevisionTargetsUseRevisionSuffixedNames() {
        InpFile master = InpFile.create("관망도.inp", "inp");
        String id = master.getInpFileId();
        InpFileRevision rev1 = InpFileRevision.create(
                id, 1, master.storFileNmForRev(1), 3, null, RevisionWorkType.EDIT);
        InpFileRevision rev2 = InpFileRevision.create(
                id, 2, master.storFileNmForRev(2), 3, null, RevisionWorkType.OPTIMIZE);
        storage.store("[1]".getBytes(MS949), rev1.getStorFileNm());
        storage.store("[2]".getBytes(MS949), rev2.getStorFileNm());
        when(repository.findById(id)).thenReturn(Optional.of(master));
        when(revisionRepository.findByInpFileIdAndRevNo(id, 1)).thenReturn(Optional.of(rev1));
        when(revisionRepository.findByInpFileIdAndRevNo(id, 2)).thenReturn(Optional.of(rev2));

        // 같은 파일의 rev1/rev2 + rev1 중복 요청
        List<InpFileDownload> targets = inpFileService.downloadRevisionTargets(List.of(
                new InpFileRevisionRef(id, 1),
                new InpFileRevisionRef(id, 2),
                new InpFileRevisionRef(id, 1)));

        // 중복 제거 후 2건, 표시명은 확장자 앞에 _r{revNo} 접미사로 충돌 없이 구분된다
        assertThat(targets).extracting(InpFileDownload::orgnlFileNm)
                .containsExactly("관망도_r1.inp", "관망도_r2.inp");
    }

    @Test
    @DisplayName("특정 리비전 다중 다운로드: 대상 리비전이 없으면 예외를 던진다")
    void downloadRevisionTargetsThrowWhenRevisionMissing() {
        InpFile master = InpFile.create("관망도.inp", "inp");
        String id = master.getInpFileId();
        when(repository.findById(id)).thenReturn(Optional.of(master));
        when(revisionRepository.findByInpFileIdAndRevNo(id, 9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inpFileService.downloadRevisionTargets(List.of(new InpFileRevisionRef(id, 9))))
                .isInstanceOf(RestApiException.class);
    }

    @Test
    @DisplayName("목록: 현재 적용 리비전에만 current=true 가 매겨진다")
    void listRevisionsMarksCurrent() {
        InpFile master = InpFile.create("a.inp", "inp");
        master.applyRevision(1); // 현재 rev1
        String id = master.getInpFileId();
        List<InpFileRevision> history = List.of(
                InpFileRevision.create(id, 2, master.storFileNmForRev(2), 3, null, RevisionWorkType.EDIT),
                InpFileRevision.create(id, 1, master.storFileNmForRev(1), 3, null, RevisionWorkType.EDIT),
                InpFileRevision.create(id, 0, master.storFileNmForRev(0), 3, null, RevisionWorkType.ORIGIN));
        when(repository.findById(id)).thenReturn(Optional.of(master));
        when(revisionRepository.findByInpFileIdOrderByRevNoDesc(id)).thenReturn(history);

        List<InpFileRevisionResponse> res = inpFileService.listRevisions(id);

        assertThat(res).hasSize(3);
        assertThat(res).filteredOn(InpFileRevisionResponse::current)
                .extracting(InpFileRevisionResponse::revNo)
                .containsExactly(1);
        assertThat(res.get(2).workType()).isEqualTo(RevisionWorkType.ORIGIN);
        // 다운로드 파일명은 특정 리비전 다운로드와 동일한 _r{revNo} 명명 규칙을 따른다(최신순 rev2/rev1/rev0).
        assertThat(res).extracting(InpFileRevisionResponse::downloadFileNm)
                .containsExactly("a_r2.inp", "a_r1.inp", "a_r0.inp");
    }
}
