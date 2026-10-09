package com.mindone.editor.inp.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mindone.editor.inp.domain.InpFile;
import com.mindone.editor.inp.domain.InpFileRevision;
import com.mindone.editor.inp.dto.InpFileResponse;
import com.mindone.editor.inp.network.combiner.GeoJsonCombiner;
import com.mindone.editor.inp.network.combiner.NetworkAssembler;
import com.mindone.editor.inp.network.combiner.NonVisualSectionCombiner;
import com.mindone.editor.inp.network.combiner.OptionsCombiner;
import com.mindone.editor.inp.network.compose.InpComposer;
import com.mindone.editor.inp.network.config.NetworkProperties;
import com.mindone.editor.inp.network.dto.NetworkDetailResponse;
import com.mindone.editor.inp.network.dto.NetworkSaveRequest;
import com.mindone.editor.inp.network.intake.InpFileReader;
import com.mindone.editor.inp.network.parser.InpParser;
import com.mindone.editor.inp.network.service.NetworkComposeService;
import com.mindone.editor.inp.network.service.NetworkService;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실제 사용 패턴 검증 — <b>"상세조회 응답을 그대로 받아 일부만 수정해 저장한다"</b>.
 *
 * <p>프론트는 {@code GET .../network} 응답 JSON 을 편집 상태로 들고 있다가, 수정한 필드만 바꿔
 * {@code PUT .../network} 로 되돌려 보낸다. 이 왕복이 깨지지 않는지( = 조회 응답의 모든 키를 저장이
 * 받아들이고, 수정하지 않은 부분은 손실 없이 보존되는지) 서비스 계층 전체를 이어 붙여 확인한다.</p>
 *
 * <p>DB 는 리포지토리 목으로, 물리 저장은 {@link TempDir} 로 대체한다. 직렬화는 Spring Boot 가 HTTP 에
 * 쓰는 것과 동일한 {@link Jackson2ObjectMapperBuilder} 매퍼를 사용해 컨트롤러 경로를 재현한다.</p>
 */
class NetworkEditRoundTripTest {

    /** Spring Boot 가 @RestController 응답/요청에 쓰는 것과 동일 설정의 매퍼. */
    private static final ObjectMapper MAPPER = Jackson2ObjectMapperBuilder.json().build();

    @TempDir
    Path storageRoot;

    private InpFileRepository repository;
    private InpFileRevisionRepository revisionRepository;
    private InpFileService inpFileService;
    private NetworkService networkService;
    private NetworkComposeService networkComposeService;

    /** 기본 샘플(제어는 규칙만) — 단순 제어가 비어 있는 경우. */
    private static final String SAMPLE = "/sample/network-sample.inp";
    /**
     * 보강 샘플({@code doc/sample/composed_enriched.inp} 를 고정한 것) — 단순 제어 2줄 + 규칙 8개에
     * 탱크/혼합/이미터/수질/공급원/다중수요/태그까지 모두 들어 있어, 기본 샘플이 못 덮는 조합을 덮는다.
     */
    private static final String ENRICHED = "/sample/composed-enriched.inp";

    private String inpFileId;

    @BeforeEach
    void setUp() throws IOException {
        InpFileStorage storage = new InpFileStorage(new StorageProperties(storageRoot));

        repository = mock(InpFileRepository.class);
        when(repository.saveAndFlush(any(InpFile.class))).thenAnswer(inv -> inv.getArgument(0));
        revisionRepository = mock(InpFileRevisionRepository.class);
        when(revisionRepository.saveAndFlush(any(InpFileRevision.class))).thenAnswer(inv -> inv.getArgument(0));

        inpFileService = new InpFileService(repository, revisionRepository, storage);
        networkService = new NetworkService(repository, revisionRepository, storage,
                new InpFileReader(), new InpParser(), new NetworkAssembler(), new GeoJsonCombiner(),
                new NonVisualSectionCombiner(), new OptionsCombiner(), new NetworkProperties(null));
        networkComposeService = new NetworkComposeService(new InpComposer(), new InpWriter(), inpFileService);

    }

    /** 원본 업로드 상태(rev0)를 만든다 — 이후 조회는 현재 적용 리비전을 가리킨다. */
    private void upload(String resource) throws IOException {
        InpFileResponse uploaded = inpFileService.saveBytes(readResource(resource), "원본관망도.inp");
        inpFileId = uploaded.inpFileId();
        stubCurrentRevision();
        when(revisionRepository.findMaxRevNo(inpFileId)).thenReturn(0);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {SAMPLE, ENRICHED})
    @DisplayName("편집 왕복: 조회 응답을 그대로 저장하면 수정한 값만 바뀌고 나머지는 전부 보존된다")
    void editedDetailResponseSurvivesSaveAndRefetch(String resource) throws IOException {
        upload(resource);
        // ── 1) 조회: 프론트가 받는 응답 그대로 ────────────────────────────────
        ObjectNode edited = (ObjectNode) MAPPER.valueToTree(networkService.getNetwork(inpFileId));
        ObjectNode sections = (ObjectNode) edited.get("sections");

        // 응답 계약 확인: 최상위 options 는 없고, 설정은 sections.OPTIONS, 제어는 sections.CONTROLS{simple,rule}
        assertThat(edited.fieldNames()).toIterable().containsExactlyInAnyOrder("meta", "layers", "sections");
        assertThat(sections.has("RULES")).as("RULES 는 CONTROLS 로 흡수되어 별도 키가 없다").isFalse();
        assertThat(sections.get("CONTROLS").fieldNames()).toIterable().containsExactly("simple", "rule");
        assertThat(sections.get("OPTIONS").has("hydraulics")).isTrue();

        int ruleCountBefore = sections.get("CONTROLS").get("rule").size();
        int simpleCountBefore = sections.get("CONTROLS").get("simple").size();
        assertThat(ruleCountBefore).isEqualTo(8);

        // ── 2) 편집: 프론트가 하듯 응답 트리의 일부 값만 바꾼다 ──────────────────
        // (a) 절점 속성 수정
        ObjectNode firstNode = (ObjectNode) edited.path("layers").path("nodeLayer").path("features").get(0)
                .get("properties");
        String editedNodeId = firstNode.get("id").asText();
        firstNode.put("elevation", 123.45);
        // (b) 단순 제어 추가(규칙 제어는 건드리지 않는다)
        ((ArrayNode) sections.get("CONTROLS").get("simple"))
                .add("LINK " + firstLinkId(edited) + " OPEN AT TIME 3");
        // (c) 설정 수정
        ((ObjectNode) sections.get("OPTIONS").get("times")).put("totalDuration", "12:00:00");

        // ── 3) 저장: 편집한 응답을 그대로 저장 요청으로 보낸다(덮어쓰기) ──────────
        NetworkSaveRequest request = MAPPER.treeToValue(edited, NetworkSaveRequest.class);
        InpFileResponse saved = networkComposeService.overwrite(inpFileId, request);
        assertThat(saved.currRevNo()).isEqualTo(1);

        // ── 4) 재조회: 저장된 새 리비전을 다시 읽는다 ──────────────────────────
        stubCurrentRevision();
        ObjectNode refetched = (ObjectNode) MAPPER.valueToTree(networkService.getNetwork(inpFileId));
        ObjectNode refetchedSections = (ObjectNode) refetched.get("sections");

        // 4-a) 수정한 값이 반영됐다
        assertThat(nodeProps(refetched, editedNodeId).get("elevation").asDouble()).isEqualTo(123.45);
        assertThat(refetchedSections.get("CONTROLS").get("simple").toString()).contains("OPEN AT TIME 3");
        assertThat(refetchedSections.get("OPTIONS").get("times").get("totalDuration").asText())
                .isEqualTo("12:00:00");

        // 4-b) 건드리지 않은 부분은 손실 없이 그대로다 — sections 전체를 편집본과 비교
        assertThat(refetchedSections.toString())
                .as("수정하지 않은 섹션/설정/제어가 저장·재조회에서 변형되지 않아야 한다")
                .isEqualTo(sections.toString());

        // 4-c) 객체 수와 제어(단순/규칙) 개수도 보존된다 — 추가한 단순 제어 1줄만 늘어난다
        assertThat(refetched.path("meta").path("layerCounts").toString())
                .isEqualTo(edited.path("meta").path("layerCounts").toString());
        assertThat(refetchedSections.get("CONTROLS").get("rule").size()).isEqualTo(ruleCountBefore);
        assertThat(refetchedSections.get("CONTROLS").get("simple").size()).isEqualTo(simpleCountBefore + 1);
    }

    @Test
    @DisplayName("보강 샘플 편집 왕복: 단순 제어와 규칙 제어가 함께 있어도 서로 섞이거나 유실되지 않는다")
    void enrichedSampleKeepsSimpleAndRuleControlsSeparate() throws IOException {
        upload(ENRICHED);

        ObjectNode detail = (ObjectNode) MAPPER.valueToTree(networkService.getNetwork(inpFileId));
        JsonNode controls = detail.path("sections").path("CONTROLS");

        // 이 샘플은 두 종류의 제어를 모두 갖는다(기본 샘플은 simple 이 비어 있어 이 조합을 못 덮는다)
        assertThat(controls.get("simple")).hasSize(2);
        assertThat(controls.get("rule")).hasSize(8);
        assertThat(controls.get("simple").get(0).asText()).contains("LINK 샘플관로 OPEN IF NODE 샘플배수지");
        // 규칙 본문이 simple 쪽으로 새어 들어가지 않는다
        assertThat(controls.get("simple").toString()).doesNotContain("RULE ").doesNotContain("PRIORITY");
        assertThat(controls.get("rule").get(0).get("content").asText()).contains("IF Pipe 10 flow");

        // 저장 → 재조회 후에도 두 갈래가 그대로 유지된다
        networkComposeService.overwrite(inpFileId, MAPPER.treeToValue(detail, NetworkSaveRequest.class));
        stubCurrentRevision();
        JsonNode refetched = MAPPER.valueToTree(networkService.getNetwork(inpFileId));

        assertThat(refetched.path("sections").path("CONTROLS").toString()).isEqualTo(controls.toString());
        // 보강 샘플에만 있는 탱크/혼합/이미터 등 객체 속성도 왕복에서 살아남는다
        assertThat(refetched.path("meta").path("layerCounts").path("tanks").asInt()).isEqualTo(1);
        assertThat(nodeProps(refetched, "샘플배수지").path("mixingModel").asText()).isEqualTo("2COMP");
        assertThat(nodeProps(refetched, "샘플배수지").path("volumeCurve").asText()).isEqualTo("TANK_VOL");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {SAMPLE, ENRICHED})
    @DisplayName("편집 왕복: 조회→저장→재조회를 두 번 반복해도 내용이 더 이상 변하지 않는다(고정점)")
    void repeatedSaveIsStable(String resource) throws IOException {
        upload(resource);
        // 1회차: 무편집 저장
        ObjectNode first = (ObjectNode) MAPPER.valueToTree(networkService.getNetwork(inpFileId));
        networkComposeService.overwrite(inpFileId, MAPPER.treeToValue(first, NetworkSaveRequest.class));
        stubCurrentRevision();
        ObjectNode second = (ObjectNode) MAPPER.valueToTree(networkService.getNetwork(inpFileId));

        // 2회차: 다시 무편집 저장
        when(revisionRepository.findMaxRevNo(inpFileId)).thenReturn(1);
        networkComposeService.overwrite(inpFileId, MAPPER.treeToValue(second, NetworkSaveRequest.class));
        stubCurrentRevision();
        ObjectNode third = (ObjectNode) MAPPER.valueToTree(networkService.getNetwork(inpFileId));

        // 저장을 반복해도 sections(제어/설정 포함)와 레이어가 계속 동일하다 — 왕복 손실 없음
        assertThat(third.get("sections").toString()).isEqualTo(second.get("sections").toString());
        assertThat(third.get("layers").toString()).isEqualTo(second.get("layers").toString());
    }

    // ===== 헬퍼 =====

    /** 최근 저장된 마스터/리비전을 현재 적용 리비전으로 조회되도록 목을 갱신한다. */
    private void stubCurrentRevision() {
        ArgumentCaptor<InpFile> masterCap = ArgumentCaptor.forClass(InpFile.class);
        ArgumentCaptor<InpFileRevision> revCap = ArgumentCaptor.forClass(InpFileRevision.class);
        verify(repository, atLeastOnce()).saveAndFlush(masterCap.capture());
        verify(revisionRepository, atLeastOnce()).saveAndFlush(revCap.capture());
        InpFile master = masterCap.getValue();
        InpFileRevision current = revCap.getValue();

        when(repository.findById(master.getInpFileId())).thenReturn(Optional.of(master));
        List<Object[]> joined = Collections.singletonList(new Object[]{master, current});
        when(repository.findWithCurrentRevisionById(master.getInpFileId())).thenReturn(joined);
    }

    /** 응답 레이어에서 특정 ID 절점의 properties 를 찾는다. */
    private JsonNode nodeProps(JsonNode response, String nodeId) {
        for (JsonNode f : response.path("layers").path("nodeLayer").path("features")) {
            if (nodeId.equals(f.path("properties").path("id").asText())) {
                return f.get("properties");
            }
        }
        throw new AssertionError("절점을 찾지 못했다: " + nodeId);
    }

    private String firstLinkId(JsonNode response) {
        return response.path("layers").path("linkLayer").path("features").get(0)
                .path("properties").path("id").asText();
    }

    private static byte[] readResource(String name) throws IOException {
        try (InputStream in = NetworkEditRoundTripTest.class.getResourceAsStream(name)) {
            assertThat(in).as("테스트 리소스 %s 존재", name).isNotNull();
            return in.readAllBytes();
        }
    }
}
