package com.mindone.editor.inp.controller;

import com.mindone.editor.common.controller.CommonController;
import com.mindone.editor.common.response.ResponseObject;
import com.mindone.editor.inp.dto.InpFileDeleteRequest;
import com.mindone.editor.inp.dto.InpFileDownload;
import com.mindone.editor.inp.dto.InpFileMonitoringRequest;
import com.mindone.editor.inp.dto.InpFileResponse;
import com.mindone.editor.inp.dto.InpFileRevisionResponse;
import com.mindone.editor.inp.dto.InpFileRevisionZipDownloadRequest;
import com.mindone.editor.inp.dto.InpFileUploadRequest;
import com.mindone.editor.inp.dto.InpFileZipDownloadRequest;
import com.mindone.editor.inp.service.InpFileService;
import com.mindone.editor.inp.storage.InpFileZipWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * INP 파일 업로드/관리 요청 명세.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "INP 파일", description = "INP 파일 업로드/관리")
public class InpFileController extends CommonController {

    /** ZIP 다운로드 파일명 접두어. */
    private static final String ZIP_NAME_PREFIX = "inp-files-";
    private static final DateTimeFormatter ZIP_NAME_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final MediaType APPLICATION_ZIP = MediaType.parseMediaType("application/zip");

    private final InpFileService inpFileService;
    private final InpFileZipWriter inpFileZipWriter;

    @Operation(
            summary = "INP 파일 업로드",
            description = "INP 파일을 스토리지에 저장(파일명은 UUID 로 변환)하고 메타데이터를 등록한다. "
                    + "사용자가 입력한 원본 파일명(info.orgnlFileNm)은 표시용으로 보관된다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @PostMapping(
            value = "/inp-files",
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ResponseObject<InpFileResponse>> uploadInpFile(
            @Parameter(
                    name = "file",
                    description = "업로드할 INP 파일",
                    required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary"))
            )
            @RequestPart("file") MultipartFile file,
            @Parameter(
                    name = "info",
                    description = "업로드 요청 정보(원본 파일명 등)",
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = InpFileUploadRequest.class))
            )
            @RequestPart("info") InpFileUploadRequest info
    ) {
        return getResponseEntity(inpFileService.upload(file, info.orgnlFileNm()));
    }

    @Operation(
            summary = "INP 파일 목록 조회",
            description = "등록된 INP 파일 목록을 등록일시 내림차순(최신순)으로 조회한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/inp-files", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<List<InpFileResponse>>> getInpFileList() {
        return getResponseEntity(inpFileService.getList());
    }

    @Operation(
            summary = "INP 파일 단일 다운로드",
            description = "INP 파일 1건을 다운로드한다. 다운로드 파일명은 원본 파일명(orgnlFileNm)으로 내려간다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping("/inp-files/{inpFileId}/download")
    public ResponseEntity<Resource> downloadInpFile(@PathVariable("inpFileId") String inpFileId) {
        InpFileDownload target = inpFileService.download(inpFileId);
        // 한글 파일명은 RFC 5987(UTF-8) 로 인코딩한다.
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(target.orgnlFileNm(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(target.fileSz())
                .body(target.resource());
    }

    @Operation(
            summary = "INP 파일 다중 ZIP 다운로드",
            description = "선택한 여러 INP 파일을 ZIP 으로 압축해 다운로드한다. ZIP 엔트리명은 원본 파일명을 사용한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @PostMapping(value = "/inp-files/download-zip", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamingResponseBody> downloadInpFilesAsZip(@RequestBody InpFileZipDownloadRequest request) {
        // 대상 검증/리소스 로드는 스트리밍 시작 전에 끝내, 누락 시 정상적으로 에러 응답이 나가도록 한다.
        List<InpFileDownload> targets = inpFileService.downloadTargets(request.inpFileIds());
        String zipName = ZIP_NAME_PREFIX + LocalDateTime.now().format(ZIP_NAME_TIMESTAMP) + ".zip";
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(zipName, StandardCharsets.UTF_8)
                .build();
        StreamingResponseBody body = out -> inpFileZipWriter.write(targets, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(APPLICATION_ZIP)
                .body(body);
    }

    @Operation(
            summary = "INP 파일 리비전(이력) 목록 조회",
            description = "특정 INP 파일의 리비전 이력을 리비전 번호 내림차순(최신 우선)으로 조회한다. "
                    + "각 항목은 리비전 번호/작업구분(ORIGIN·EDIT·OPTIMIZE)/저장 파일명/다운로드 파일명(_r{리비전번호} 접미사)/"
                    + "크기/현재 적용 여부/등록일시를 포함한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/inp-files/{inpFileId}/revisions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<List<InpFileRevisionResponse>>> getInpFileRevisions(
            @PathVariable("inpFileId") String inpFileId) {
        return getResponseEntity(inpFileService.listRevisions(inpFileId));
    }

    @Operation(
            summary = "INP 파일 특정 리비전 다운로드",
            description = "특정 리비전의 INP 파일을 다운로드한다. 다운로드 파일명은 원본 파일명 확장자 앞에 _r{리비전번호} 를 붙여 구분한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping("/inp-files/{inpFileId}/revisions/{revNo}/download")
    public ResponseEntity<Resource> downloadInpFileRevision(
            @PathVariable("inpFileId") String inpFileId,
            @PathVariable("revNo") int revNo) {
        InpFileDownload target = inpFileService.downloadRevision(inpFileId, revNo);
        // 한글 파일명은 RFC 5987(UTF-8) 로 인코딩한다.
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(target.orgnlFileNm(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(target.fileSz())
                .body(target.resource());
    }

    @Operation(
            summary = "INP 파일 특정 리비전 다중 ZIP 다운로드",
            description = "선택한 여러 (INP 파일 + 리비전 번호)의 파일을 ZIP 으로 압축해 다운로드한다. 현재 적용 리비전이 "
                    + "아니라 각 항목이 지정한 리비전의 파일을 담으며, ZIP 엔트리명은 원본 파일명 확장자 앞에 "
                    + "_r{리비전번호} 를 붙여 구분한다(같은 파일의 여러 리비전도 충돌 없이 담긴다)."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @PostMapping(value = "/inp-files/revisions/download-zip", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamingResponseBody> downloadInpFileRevisionsAsZip(
            @RequestBody InpFileRevisionZipDownloadRequest request) {
        // 대상 검증/리소스 로드는 스트리밍 시작 전에 끝내, 누락 시 정상적으로 에러 응답이 나가도록 한다.
        List<InpFileDownload> targets = inpFileService.downloadRevisionTargets(request.revisions());
        String zipName = ZIP_NAME_PREFIX + LocalDateTime.now().format(ZIP_NAME_TIMESTAMP) + ".zip";
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(zipName, StandardCharsets.UTF_8)
                .build();
        StreamingResponseBody body = out -> inpFileZipWriter.write(targets, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .contentType(APPLICATION_ZIP)
                .body(body);
    }

    @Operation(
            summary = "INP 파일 리비전 롤백",
            description = "현재 적용 리비전을 기존 리비전으로 되돌린다. 새 리비전이나 물리 파일을 만들지 않고 "
                    + "마스터의 현재 리비전 포인터만 대상 리비전으로 이동한다. 응답은 갱신된 파일 메타데이터(현재 리비전 = 대상)다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @PutMapping(value = "/inp-files/{inpFileId}/revisions/{revNo}/rollback", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<InpFileResponse>> rollbackInpFileRevision(
            @PathVariable("inpFileId") String inpFileId,
            @PathVariable("revNo") int revNo) {
        return getResponseEntity(inpFileService.rollback(inpFileId, revNo));
    }

    @Operation(
            summary = "INP 파일 모니터링 여부 변경",
            description = "INP 파일을 모니터링 대상으로 설정하거나 해제한다. 마스터의 모니터링 플래그만 변경하며 "
                    + "리비전/물리 파일에는 영향이 없다. 응답은 갱신된 파일 메타데이터(현재 적용 리비전 기준)다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @PutMapping(
            value = "/inp-files/{inpFileId}/monitoring",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ResponseObject<InpFileResponse>> changeInpFileMonitoring(
            @PathVariable("inpFileId") String inpFileId,
            @RequestBody InpFileMonitoringRequest request) {
        return getResponseEntity(inpFileService.changeMonitoring(inpFileId, request.monitoringYn()));
    }

    @Operation(
            summary = "INP 파일 단일 삭제",
            description = "INP 파일 1건을 삭제한다. 레코드를 삭제하고, 에러가 없으면 물리 파일도 삭제한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @DeleteMapping("/inp-files/{inpFileId}")
    public ResponseEntity<ResponseObject<Void>> deleteInpFile(@PathVariable("inpFileId") String inpFileId) {
        inpFileService.delete(inpFileId);
        return getResponseEntity();
    }

    @Operation(
            summary = "INP 파일 다중 삭제",
            description = "선택한 여러 INP 파일을 삭제한다. 레코드를 일괄 삭제하고, 에러가 없으면 물리 파일도 삭제한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @DeleteMapping(value = "/inp-files", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<Void>> deleteInpFiles(@RequestBody InpFileDeleteRequest request) {
        inpFileService.deleteAll(request.inpFileIds());
        return getResponseEntity();
    }
}