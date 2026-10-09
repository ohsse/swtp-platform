package com.mindone.editor.inp.vismapping.controller;

import com.mindone.editor.common.controller.CommonController;
import com.mindone.editor.common.response.ResponseObject;
import com.mindone.editor.inp.vismapping.dto.InpVisMappingRequest;
import com.mindone.editor.inp.vismapping.dto.InpVisMappingResponse;
import com.mindone.editor.inp.vismapping.service.InpVisMappingService;
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
 * INP 시각화 매핑 요청 명세.
 *
 * <p>특정 INP 파일에 대한 시각화 매핑(junction + pipe + 유량태그번호 + 압력태그번호 + 지점명)을 CRUD 한다.
 * 모든 엔드포인트는 경로의 {@code inpFileId} 하위에 속한다(파일 단위 1:N).</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "INP 시각화 매핑", description = "INP 파일 시각화 매핑(junction/pipe/유량태그/압력태그/지점명) 관리")
public class InpVisMappingController extends CommonController {

    private final InpVisMappingService inpVisMappingService;

    @Operation(
            summary = "매핑 등록",
            description = "특정 INP 파일에 시각화 매핑을 등록한다. junction/pipe 는 필수, 유량태그번호/압력태그번호/지점명은 선택이다. "
                    + "같은 파일 안에서 같은 junction 의 중복 매핑은 허용하지 않는다(DUPLICATE_MAPPING)."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @PostMapping(value = "/inp-files/{inpFileId}/mappings",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<InpVisMappingResponse>> createMapping(
            @PathVariable("inpFileId") String inpFileId,
            @RequestBody InpVisMappingRequest request) {
        return getResponseEntity(inpVisMappingService.create(inpFileId, request));
    }

    @Operation(
            summary = "매핑 목록 조회",
            description = "특정 INP 파일의 매핑 목록을 등록순으로 조회한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/inp-files/{inpFileId}/mappings", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<List<InpVisMappingResponse>>> getMappings(
            @PathVariable("inpFileId") String inpFileId) {
        return getResponseEntity(inpVisMappingService.list(inpFileId));
    }

    @Operation(
            summary = "매핑 단건 조회",
            description = "특정 INP 파일의 매핑 1건을 조회한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/inp-files/{inpFileId}/mappings/{mappingId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<InpVisMappingResponse>> getMapping(
            @PathVariable("inpFileId") String inpFileId,
            @PathVariable("mappingId") Long mappingId) {
        return getResponseEntity(inpVisMappingService.get(inpFileId, mappingId));
    }

    @Operation(
            summary = "매핑 수정",
            description = "특정 INP 파일의 매핑 1건을 수정한다. junction/pipe 는 필수, 유량태그번호/압력태그번호/지점명은 선택이다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @PutMapping(value = "/inp-files/{inpFileId}/mappings/{mappingId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<InpVisMappingResponse>> updateMapping(
            @PathVariable("inpFileId") String inpFileId,
            @PathVariable("mappingId") Long mappingId,
            @RequestBody InpVisMappingRequest request) {
        return getResponseEntity(inpVisMappingService.update(inpFileId, mappingId, request));
    }

    @Operation(
            summary = "매핑 삭제",
            description = "특정 INP 파일의 매핑 1건을 삭제한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @DeleteMapping("/inp-files/{inpFileId}/mappings/{mappingId}")
    public ResponseEntity<ResponseObject<Void>> deleteMapping(
            @PathVariable("inpFileId") String inpFileId,
            @PathVariable("mappingId") Long mappingId) {
        inpVisMappingService.delete(inpFileId, mappingId);
        return getResponseEntity();
    }
}
