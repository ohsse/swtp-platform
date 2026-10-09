package com.mindone.editor.inp.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindone.editor.common.exception.RestApiException;
import com.mindone.editor.inp.domain.InpFile;
import com.mindone.editor.inp.domain.InpFileRevision;
import com.mindone.editor.inp.dto.InpFileResponse;
import com.mindone.editor.inp.network.combiner.GeoJsonCombiner;
import com.mindone.editor.inp.network.combiner.NetworkAssembler;
import com.mindone.editor.inp.network.compose.InpComposer;
import com.mindone.editor.inp.network.dto.NetworkLayers;
import com.mindone.editor.inp.network.dto.NetworkSaveRequest;
import com.mindone.editor.inp.network.intake.InpFileReader;
import com.mindone.editor.inp.network.parser.InpParser;
import com.mindone.editor.inp.network.service.NetworkComposeService;
import com.mindone.editor.inp.network.writer.InpWriter;
import com.mindone.editor.inp.repository.InpFileRepository;
import com.mindone.editor.inp.repository.InpFileRevisionRepository;
import com.mindone.editor.inp.service.InpFileService;
import com.mindone.editor.inp.storage.InpFileStorage;
import com.mindone.editor.storage.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "다른 이름으로 저장" / "원본 덮어쓰기" 검증 — 리비전(이력) 기반.
 *
 * <p>물리 저장은 임시 디렉터리({@link TempDir})에 수행하고, 메타 영속화는 리포지토리를 목으로 대체해
 * DB 없이 검증한다. 핵심:</p>
 * <ul>
 *   <li>새로쓰기: 원본은 건드리지 않고 새 마스터 + rev0(EDIT) 물리 파일이 추가된다.</li>
 *   <li>덮어쓰기: in-place 가 아니라 <b>새 리비전 파일</b>이 생성되고 마스터 포인터가 그 리비전으로 이동한다.</li>
 * </ul>
 */
class NetworkSaveAsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Charset MS949 = Charset.forName("MS949");

    @TempDir
    Path storageRoot;

    private InpFileRepository repository;
    private InpFileRevisionRepository revisionRepository;
    private InpFileService inpFileService;
    private NetworkComposeService networkComposeService;

    // 저장 결과 검증용 포워드 파이프라인
    private final InpFileReader reader = new InpFileReader();
    private final InpParser parser = new InpParser();
    private final NetworkAssembler assembler = new NetworkAssembler();
    private final GeoJsonCombiner geoJsonCombiner = new GeoJsonCombiner();

    @BeforeEach
    void setUp() {
        StorageProperties storageProperties = new StorageProperties(storageRoot);
        InpFileStorage storage = new InpFileStorage(storageProperties);

        // 리포지토리는 목: saveAndFlush 는 전달된 엔티티를 그대로 돌려준다(DB 불필요)
        repository = mock(InpFileRepository.class);
        when(repository.saveAndFlush(any(InpFile.class))).thenAnswer(inv -> inv.getArgument(0));
        revisionRepository = mock(InpFileRevisionRepository.class);
        when(revisionRepository.saveAndFlush(any(InpFileRevision.class))).thenAnswer(inv -> inv.getArgument(0));

        inpFileService = new InpFileService(repository, revisionRepository, storage);
        networkComposeService = new NetworkComposeService(new InpComposer(), new InpWriter(), inpFileService);
    }

    @Test
    @DisplayName("바이트 저장: 새 마스터 + rev0(EDIT) 물리 파일이 추가되고 내용이 정확하다")
    void saveBytesAddsNewRecordAndFile() throws IOException {
        byte[] content = "[TITLE]\n\n[END]\n".getBytes(MS949);

        InpFileResponse res = inpFileService.saveBytes(content, "편집본");

        // 메타: 새 UUID, .inp 확장자 자동 보정, 크기 일치, 현재 리비전 0
        assertThat(res.inpFileId()).hasSize(36);
        assertThat(res.orgnlFileNm()).isEqualTo("편집본.inp");
        assertThat(res.fileXtns()).isEqualTo("inp");
        assertThat(res.fileSz()).isEqualTo(content.length);
        assertThat(res.currRevNo()).isZero();
        assertThat(res.storFileNm()).isEqualTo(res.inpFileId() + "_r0.inp");
        verify(repository, times(1)).saveAndFlush(any(InpFile.class));
        verify(revisionRepository, times(1)).saveAndFlush(any(InpFileRevision.class));

        // 물리 파일: originals 하위에 새 저장 파일명으로 기록되고 바이트가 동일
        Path stored = storageRoot.resolve("originals").resolve(res.storFileNm());
        assertThat(stored).exists();
        assertThat(Files.readAllBytes(stored)).isEqualTo(content);
    }

    @Test
    @DisplayName("확장자 보존: 이미 .inp 면 중복으로 붙이지 않는다")
    void keepsExistingInpExtension() {
        byte[] content = "[END]\n".getBytes();
        InpFileResponse res = inpFileService.saveBytes(content, "관망도.inp");
        assertThat(res.orgnlFileNm()).isEqualTo("관망도.inp");
    }

    @Test
    @DisplayName("다른 이름으로 저장: response.json 편집본을 새 파일로 추가하고, 저장본 재파싱이 원본 네트워크와 동일하다")
    void saveAsAddsEditedNetworkAsNewFile() throws IOException {
        NetworkSaveRequest request = readSaveRequest(readResource("/sample/response.json"));

        InpFileResponse res = networkComposeService.saveAs(request, "편집본_관망도");

        // 새 마스터 + rev0 가 추가된다(새 UUID, 지정한 새 파일명)
        assertThat(res.inpFileId()).hasSize(36);
        assertThat(res.orgnlFileNm()).isEqualTo("편집본_관망도.inp");
        assertThat(res.currRevNo()).isZero();
        assertThat(res.fileSz()).isPositive();
        verify(repository, times(1)).saveAndFlush(any(InpFile.class));
        verify(revisionRepository, times(1)).saveAndFlush(any(InpFileRevision.class));

        // 저장된 물리 파일을 다시 파싱하면 편집한 네트워크가 그대로 복원된다(객체 수 보존)
        Path stored = storageRoot.resolve("originals").resolve(res.storFileNm());
        assertThat(stored).exists();
        byte[] savedBytes = Files.readAllBytes(stored);
        NetworkLayers layers = geoJsonCombiner.toLayers(
                assembler.assemble(parser.parse(reader.read(savedBytes).lines())));
        assertThat(layers.nodeLayer().features()).hasSize(515);
        assertThat(layers.linkLayer().features()).hasSize(531);
        assertThat(layers.labelLayer().features()).hasSize(87);

        // 저장본은 CP949 로 한글이 보존된다
        String decoded = new String(savedBytes, MS949);
        assertThat(decoded).contains("고산분기");
        assertThat(decoded.indexOf('�')).isEqualTo(-1);
    }

    @Test
    @DisplayName("원본 덮어쓰기: 새 리비전 파일을 만들고 마스터 포인터를 그 리비전으로 이동한다")
    void overwriteCreatesNewRevisionAndMovesPointer() throws IOException {
        // 기존(원본) 마스터를 준비한다. rev0 이 이미 존재하므로 max(rev_no)=0 → 다음 리비전은 1.
        InpFile master = InpFile.create("원본관망도.inp", "inp");
        when(repository.findById(master.getInpFileId())).thenReturn(Optional.of(master));
        when(revisionRepository.findMaxRevNo(master.getInpFileId())).thenReturn(0);

        // 편집 내용으로 덮어쓴다
        NetworkSaveRequest request = readSaveRequest(readResource("/sample/response.json"));
        InpFileResponse res = networkComposeService.overwrite(master.getInpFileId(), request);

        // 같은 마스터(ID 유지), 리비전 1 로 증가, 저장 파일명은 새 리비전 파일명
        assertThat(res.inpFileId()).isEqualTo(master.getInpFileId());
        assertThat(res.currRevNo()).isEqualTo(1);
        assertThat(res.storFileNm()).isEqualTo(master.storFileNmForRev(1));
        assertThat(master.getCurrRevNo()).isEqualTo(1); // 포인터 이동
        verify(revisionRepository, times(1)).saveAndFlush(any(InpFileRevision.class));
        // 마스터는 새로 INSERT 하지 않는다(기존 엔티티의 포인터만 변경)
        verify(repository, never()).saveAndFlush(any(InpFile.class));

        // 새 리비전 물리 파일이 생성되고 편집 내용으로 재파싱된다
        Path revFile = storageRoot.resolve("originals").resolve(res.storFileNm());
        assertThat(revFile).exists();
        byte[] saved = Files.readAllBytes(revFile);
        assertThat(res.fileSz()).isEqualTo(saved.length);
        NetworkLayers layers = geoJsonCombiner.toLayers(
                assembler.assemble(parser.parse(reader.read(saved).lines())));
        assertThat(layers.nodeLayer().features()).hasSize(515);
        assertThat(new String(saved, MS949)).contains("고산분기");
    }

    @Test
    @DisplayName("원본 덮어쓰기: 대상 ID 가 없으면 예외를 던진다")
    void overwriteMissingTargetThrows() {
        when(repository.findById("not-exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inpFileService.overwriteBytes("not-exist", "[END]\n".getBytes(MS949)))
                .isInstanceOf(RestApiException.class);
        verify(revisionRepository, never()).saveAndFlush(any(InpFileRevision.class));
    }

    // ===== 헬퍼 =====

    private NetworkSaveRequest readSaveRequest(byte[] json) {
        try {
            JsonNode data = MAPPER.readTree(json).get("data");
            return MAPPER.treeToValue(data, NetworkSaveRequest.class);
        } catch (IOException e) {
            throw new IllegalStateException("response.json 역직렬화 실패", e);
        }
    }

    private static byte[] readResource(String name) throws IOException {
        try (InputStream in = NetworkSaveAsTest.class.getResourceAsStream(name)) {
            assertThat(in).as("테스트 리소스 %s 존재", name).isNotNull();
            return in.readAllBytes();
        }
    }
}
