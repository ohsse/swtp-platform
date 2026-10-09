package com.mindone.editor.correction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 태그 보정 알림 확인(ack) 요청.
 *
 * <p>프론트가 토스트로 띄운 계측들의 복합키 목록을 담아 보낸다. 서버는 해당 행들의 알림여부를
 * {@code Y} 로 갱신해 다음 폴링에서 다시 잡히지 않게 한다.</p>
 *
 * @param keys 알림 완료로 표시할 계측 복합키 목록
 */
@Schema(description = "태그 보정 알림 확인(ack) 요청")
public record TagCorrectionAckRequest(
        @Schema(description = "알림 완료로 표시할 계측 복합키 목록") List<Key> keys
) {

    /**
     * 계측 복합키.
     *
     * @param measTs 계측시간
     * @param tagNo  태그번호
     */
    @Schema(description = "계측 복합키")
    public record Key(
            @Schema(description = "계측시간") LocalDateTime measTs,
            @Schema(description = "태그번호") String tagNo
    ) {
    }
}
