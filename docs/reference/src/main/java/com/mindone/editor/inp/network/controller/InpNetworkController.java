package com.mindone.editor.inp.network.controller;

import com.mindone.editor.common.controller.CommonController;
import com.mindone.editor.common.response.ResponseObject;
import com.mindone.editor.inp.dto.InpFileResponse;
import com.mindone.editor.inp.network.dto.NetworkDetailResponse;
import com.mindone.editor.inp.network.dto.NetworkSaveAsRequest;
import com.mindone.editor.inp.network.dto.NetworkSaveRequest;
import com.mindone.editor.inp.network.service.NetworkComposeService;
import com.mindone.editor.inp.network.service.NetworkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * INP 상세조회(네트워크 표출) 요청 명세.
 *
 * <p>저장된 INP 파일을 읽어 파싱·결합한 결과를 레이어별 GeoJSON 으로 반환한다. 파일 CRUD 를 다루는
 * {@link com.mindone.editor.inp.controller.InpFileController} 와 관심사를 분리한다.</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "INP 상세조회", description = "INP 파일 파싱 → 레이어별 GeoJSON 표출")
public class InpNetworkController extends CommonController {

    private final NetworkService networkService;
    private final NetworkComposeService networkComposeService;

    @Operation(
            summary = "INP 네트워크 상세조회(GeoJSON + 비가시 섹션 + 설정)",
            description = "저장된 INP 파일을 인코딩 자동판별 후 파싱하고, 좌표/정점과 결합해 레이어별 "
                    + "GeoJSON(절점/저수지/탱크/관로/펌프/밸브/라벨)으로 반환한다. 좌표는 원본 투영좌표이며 "
                    + "좌표계는 meta.crs 로 함께 제공된다. 비가시 객체(CURVES/PATTERNS/CONTROLS/REPORT 등)는 "
                    + "sections 에 EPANET 의미 키-밸류로 담긴다. 제어는 sections.CONTROLS 에 {simple, rule} "
                    + "(단순 제어문 / 규칙 제어)로 묶이고, 설정(OPTIONS/TIMES/REACTIONS/ENERGY)은 sections.OPTIONS 에 "
                    + "EPANET Options 브라우저 구조(hydraulics/quality/reactions/times/energy)로 담긴다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/inp-files/{inpFileId}/network", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<NetworkDetailResponse>> getNetwork(
            @PathVariable("inpFileId") String inpFileId) {
        return getResponseEntity(networkService.getNetwork(inpFileId));
    }

    @Operation(
            summary = "INP 네트워크 특정 리비전 상세조회",
            description = "현재 적용 리비전이 아닌 과거(또는 임의) 리비전의 네트워크를 상세조회한다. 동작은 현재 리비전 "
                    + "상세조회와 동일하며, 읽는 대상만 지정 리비전 번호의 저장 파일이 된다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/inp-files/{inpFileId}/revisions/{revNo}/network", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<NetworkDetailResponse>> getNetworkRevision(
            @PathVariable("inpFileId") String inpFileId,
            @PathVariable("revNo") int revNo) {
        return getResponseEntity(networkService.getNetwork(inpFileId, revNo));
    }

    @Operation(
            summary = "INP 네트워크 저장(편집 결과로 원본 INP 파일 덮어쓰기)",
            description = "프론트가 편집한 상세조회 응답(layers/sections)을 경로의 inpFileId 와 함께 받아 "
                    + "전 섹션을 복원·직렬화하고, 해당 INP 파일의 물리 파일을 그 내용으로 덮어쓴다. 상세조회의 "
                    + "역흐름(Composer → Writer)이다. 새 파일을 만들지 않고 기존 레코드/ID/파일명을 유지하며 내용만 "
                    + "교체한다(크기·수정일시 갱신). 응답은 갱신된 파일의 메타데이터다. 새 파일로 저장하려면 save-as 를 쓴다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @PutMapping(value = "/inp-files/{inpFileId}/network",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<InpFileResponse>> overwriteNetwork(
            @PathVariable("inpFileId") String inpFileId,
            @RequestBody NetworkSaveRequest request) {
        return getResponseEntity(networkComposeService.overwrite(inpFileId, request));
    }

    @Operation(
            summary = "INP 네트워크 다른 이름으로 저장(편집 결과 → 새 INP 파일 추가)",
            description = "프론트가 편집한 상세조회 응답을 새 파일명과 함께 받아 INP 로 직렬화하고, 새 INP 파일 "
                    + "레코드와 물리 파일을 추가한다. 원본 파일은 수정하지 않으므로 저장 후 파일이 1건 늘어난다. "
                    + "응답은 새로 등록된 파일의 메타데이터(새 ID)다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @PostMapping(value = "/inp-files/network/save-as",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<InpFileResponse>> saveAsNetwork(@RequestBody NetworkSaveAsRequest request) {
        return getResponseEntity(networkComposeService.saveAs(request.toSaveRequest(), request.fileName()));
    }
}
