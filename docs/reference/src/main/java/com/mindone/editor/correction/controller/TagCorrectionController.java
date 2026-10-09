package com.mindone.editor.correction.controller;

import com.mindone.editor.common.controller.CommonController;
import com.mindone.editor.common.response.ResponseObject;
import com.mindone.editor.correction.dto.TagCorrectionAckRequest;
import com.mindone.editor.correction.dto.TagCorrectionResponse;
import com.mindone.editor.correction.service.TagCorrectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 태그 보정 알림 명세.
 *
 * <p>DB 트리거가 평균값으로 보정한 계측을 프론트가 폴링해 토스트로 띄우고, 띄운 건을 알림 완료로
 * 확인(ack)한다. 원본 헌팅값 적재는 외부에서 이뤄지며, 여기서는 조회 + 알림여부 갱신만 담당한다.</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "태그 보정 알림", description = "헌팅값 평균보간 보정 결과 조회/알림 확인")
public class TagCorrectionController extends CommonController {

    private final TagCorrectionService tagCorrectionService;

    @Operation(
            summary = "보정 알림 폴링 조회",
            description = "보정 완료(corrYn=Y) + 미알림(notiYn=N) 계측을 계측시간 오름차순으로 조회한다. "
                    + "프론트가 5~10초 간격으로 폴링해 각 건을 토스트로 띄우고, 이어서 알림 확인 API 로 처리한다."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @GetMapping(value = "/tag-corrections/pending", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<List<TagCorrectionResponse>>> getPending() {
        return getResponseEntity(tagCorrectionService.findPending());
    }

    @Operation(
            summary = "보정 알림 확인(ack)",
            description = "프론트가 토스트로 띄운 계측 복합키 목록을 보내면 해당 행의 알림여부를 Y 로 갱신해 "
                    + "다음 폴링에서 다시 잡히지 않게 한다. 응답 data 는 실제로 갱신된 건수."
    )
    @ApiResponses(value = {
            @ApiResponse(description = "성공", responseCode = "200")
    })
    @PostMapping(value = "/tag-corrections/notify",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseObject<Integer>> markNotified(@RequestBody TagCorrectionAckRequest request) {
        return getResponseEntity(tagCorrectionService.markNotified(request));
    }
}
