package com.mo.swtp.common.operation;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code operation} 스키마 코드 enum 4종이 DDL과 정합한지 검증한다.
 *
 * <p>상수명이 곧 DB 코드값이라({@code @Enumerated(EnumType.STRING)}) 컬럼 폭을 넘는 상수를
 * 추가하면 <b>DB INSERT 시점에야</b> 터진다. 그 실패를 빌드로 당겨오는 것이 이 클래스의 존재 이유다.
 */
class OperationCodeTest {

    /**
     * {@code V3__operation_drvmd_chg.sql}의 코드 컬럼 폭.
     * {@code ctrl_trgt_type_cd}·{@code bf_drvmd_cd}·{@code af_drvmd_cd}·{@code iss_svc_cd}·
     * {@code chg_rsn_cd}가 모두 {@code VARCHAR(10)}이다.
     */
    private static final int CODE_COLUMN_LENGTH = 10;

    /** 코드값으로 DB에 실리는 enum 전부 — 이 패키지에 enum을 추가하면 여기에도 넣는다. */
    private static final List<Class<? extends Enum<?>>> CODE_ENUMS =
            List.of(DrivenMode.class, ChangeReason.class, ControlTargetType.class, IssuerService.class);

    /** 위 4종의 상수 총합. 루프가 실제로 돌았음을 함께 증명해 공허 참을 막는다. */
    private static final int TOTAL_CONSTANTS = 3 + 11 + 2 + 2;

    @Test
    @DisplayName("전 코드 상수명이 VARCHAR(10)에 들어간다 — 넘으면 DB INSERT 시점에야 터진다")
    void 코드값이_컬럼_폭을_넘지_않는다() {
        int counted = 0;
        for (Class<? extends Enum<?>> type : CODE_ENUMS) {
            for (Enum<?> constant : type.getEnumConstants()) {
                assertTrue(constant.name().length() <= CODE_COLUMN_LENGTH,
                        () -> "%s.%s 는 %d자로 VARCHAR(%d)를 넘는다"
                                .formatted(type.getSimpleName(), constant.name(),
                                        constant.name().length(), CODE_COLUMN_LENGTH));
                counted++;
            }
        }
        // 배열이 비어도 위 루프는 통과한다 — 실제로 검사한 개수를 함께 단언한다.
        assertEquals(TOTAL_CONSTANTS, counted);
    }

    @Test
    @DisplayName("운전모드는 AI_ANLS·AI_RCMD·AI 3값이다")
    void 운전모드_집합() {
        assertEquals(List.of("AI_ANLS", "AI_RCMD", "AI"), names(DrivenMode.class));
    }

    @Test
    @DisplayName("운전모드 level()은 자율성 오름차순이고 값마다 고유하다")
    void 운전모드_자율성_순서() {
        assertEquals(0, DrivenMode.AI_ANLS.level());
        assertEquals(1, DrivenMode.AI_RCMD.level());
        assertEquals(2, DrivenMode.AI.level());

        // 하향은 어디서든 AI_ANLS로 즉시 가능해야 한다 — 그 판정의 근거가 되는 순서다.
        assertTrue(DrivenMode.AI.level() > DrivenMode.AI_ANLS.level());
        assertEquals(DrivenMode.values().length,
                Arrays.stream(DrivenMode.values()).map(DrivenMode::level).distinct().count());
    }

    @Test
    @DisplayName("변경사유는 11종이다 — 이력은 소급 재분류가 불가능해 처음부터 갖춰 둔다")
    void 변경사유_집합() {
        assertEquals(
                List.of("OPRTR", "APRV", "ANOMALY", "ITLCK", "MDL_DGRD", "SNSR_FAIL",
                        "COMM_FAIL", "SCHED", "MAINT", "TEST", "ETC"),
                names(ChangeReason.class));
    }

    @Test
    @DisplayName("제어대상 유형은 V3 DDL 코멘트의 CTRL_GRP·PRCS와 일치한다")
    void 제어대상_유형_집합() {
        assertEquals(List.of("CTRL_GRP", "PRCS"), names(ControlTargetType.class));
    }

    @Test
    @DisplayName("발행 서비스는 EMS·AUTO 2값 — 모드를 바꿀 수 있는 화면이 그 둘뿐이다")
    void 발행_서비스_집합() {
        // 아키텍처 11.3의 3값(autonomous|ems|manual)은 제어 명령 이력의 것이다.
        // 운전모드에는 현장 직접 조작 경로가 없으므로 MANUAL을 두지 않는다 —
        // 쓰이지 않는 값은 아무도 만들 수 없는 데이터를 위한 분기를 남긴다.
        assertEquals(List.of("EMS", "AUTO"), names(IssuerService.class));
    }

    private static List<String> names(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
    }
}
