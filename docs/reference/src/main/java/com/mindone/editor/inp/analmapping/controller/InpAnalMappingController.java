package com.mindone.editor.inp.analmapping.controller;

import com.mindone.editor.common.controller.CommonController;
import com.mindone.editor.common.response.ResponseObject;
import com.mindone.editor.inp.analmapping.dto.InpAnalMappingRequest;
import com.mindone.editor.inp.analmapping.dto.InpAnalMappingResponse;
import com.mindone.editor.inp.analmapping.service.InpAnalMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * INP 분석 매핑 요청 명세.
 *
 * <p>특정 INP 파일에 대한 분석 매핑(node + 태그번호 + 데이터유형)을 CRUD 한다.
 * 모든 엔드포인트는 경로의 {@code inpFileId} 하위에 속한다(파일 단위 1:N).</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "INP 분석 매핑", description = "INP 파일 분석 매핑(node/태그번호/데이터유형) 관리")
public class InpAnalMappingController extends CommonController {

    private final InpAnalMappingService inpAnalMappingService;

    @Operation(
            summary = "매핑 등록",
            description = "특정 INP 파일에 분석 매핑을 등록한다. node/태그번호는 필수이며, 데이터유형은 태그번호로 결정된다"
                    + "(FRI→FLOW, PRI→PRESSURE, 둘 다 없으면 오류). "
                    + "같은 파일 안에서 같은 node + 데이터유형의 중복 매핑은 허용하지 않는다(DUPLICATE_MAPPING)."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @PostMapping(value = "/inp-files/{inpFileId}/anal-mappings",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<InpAnalMappingResponse>> createMapping(
            @PathVariable("inpFileId") String inpFileId,
            @RequestBody InpAnalMappingRequest request) {
        return getResponseEntity(inpAnalMappingService.create(inpFileId, request));
    }

    @Operation(
            summary = "매핑 목록 조회",
            description = "특정 INP 파일의 분석 매핑 목록을 등록순으로 조회한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/inp-files/{inpFileId}/anal-mappings", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<List<InpAnalMappingResponse>>> getMappings(
            @PathVariable("inpFileId") String inpFileId) {
        return getResponseEntity(inpAnalMappingService.list(inpFileId));
    }

    @Operation(
            summary = "매핑 단건 조회",
            description = "특정 INP 파일의 분석 매핑 1건을 조회한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/inp-files/{inpFileId}/anal-mappings/{mappingId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<InpAnalMappingResponse>> getMapping(
            @PathVariable("inpFileId") String inpFileId,
            @PathVariable("mappingId") Long mappingId) {
        return getResponseEntity(inpAnalMappingService.get(inpFileId, mappingId));
    }

    @Operation(
            summary = "매핑 수정",
            description = "특정 INP 파일의 분석 매핑 1건을 수정한다. node/태그번호는 필수이며, 데이터유형은 태그번호로 결정된다(FRI→FLOW, PRI→PRESSURE)."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @PutMapping(value = "/inp-files/{inpFileId}/anal-mappings/{mappingId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<InpAnalMappingResponse>> updateMapping(
            @PathVariable("inpFileId") String inpFileId,
            @PathVariable("mappingId") Long mappingId,
            @RequestBody InpAnalMappingRequest request) {
        return getResponseEntity(inpAnalMappingService.update(inpFileId, mappingId, request));
    }

    @Operation(
            summary = "매핑 삭제",
            description = "특정 INP 파일의 분석 매핑 1건을 삭제한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @DeleteMapping("/inp-files/{inpFileId}/anal-mappings/{mappingId}")
    public ResponseEntity<ResponseObject<Void>> deleteMapping(
            @PathVariable("inpFileId") String inpFileId,
            @PathVariable("mappingId") Long mappingId) {
        inpAnalMappingService.delete(inpFileId, mappingId);
        return getResponseEntity();
    }
}
