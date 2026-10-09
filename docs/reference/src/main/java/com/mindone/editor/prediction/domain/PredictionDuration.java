package com.mindone.editor.prediction.domain;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 예측 구간(duration).
 *
 * <p>한 (태그번호, 예측시간)에 대해 예측값이 산출되는 구간을 구분한다. 같은 태그번호·같은 예측시간이라도
 * 예측 구간이 다르면 별개의 예측값으로 본다(8시 예측데이터라면 아래 5종이 각각 존재).</p>
 *
 * <p>DB 컬럼({@code duration_cd})에는 <b>외부 예측 모듈(파이썬)이 쓰는 코드값</b>({@link #code},
 * 예: {@code 10M}/{@code 1H})이 저장된다. enum 이름(M10/H1)과 코드 형식이 다르므로
 * ({@code @Enumerated(STRING)} 은 이름 기준이라 못 씀), DB 매핑은 코드 기준
 * {@link com.mindone.editor.prediction.domain.PredictionDurationConverter} 로 처리한다.
 * {@link #label} 은 화면 표시용 한글 라벨, {@link #minutes} 는 분 단위 길이로 정렬·계산에 쓴다.
 * 상수 선언 순서가 곧 분 단위 오름차순이므로, enum ordinal 기준 정렬이 짧은 구간 → 긴 구간 순서가 된다.</p>
 *
 * <p>파이썬은 이 5종 외 구간({@code 20M/40M/50M/2H})도 {@code tag_pred_l} 에 적재하지만, 모니터링 대상은
 * 아래 5종뿐이다. 리포지토리 조회는 {@link #MONITORED} 로 대상 코드만 걸러 읽으므로 미모니터링 코드는
 * enum 변환에 도달하지 않는다.</p>
 */
@Getter
public enum PredictionDuration {

    /** 10분. */
    M10("10M", "10분", 10),
    /** 30분. */
    M30("30M", "30분", 30),
    /** 1시간. */
    H1("1H", "1시간", 60),
    /** 3시간. */
    H3("3H", "3시간", 180),
    /** 6시간. */
    H6("6H", "6시간", 360);

    /** DB 저장 코드값(파이썬 예측 모듈 규약: 10M/30M/1H/3H/6H). */
    private final String code;
    /** 화면 표시용 한글 라벨. */
    private final String label;
    /** 분 단위 구간 길이. */
    private final int minutes;

    /** 모니터링 대상 예측구간 5종(리포지토리 조회 필터용). */
    public static final List<PredictionDuration> MONITORED = List.of(values());

    PredictionDuration(String code, String label, int minutes) {
        this.code = code;
        this.label = label;
        this.minutes = minutes;
    }

    /**
     * DB 코드값을 enum 으로 변환한다.
     *
     * @param code duration_cd 값(예: {@code 10M})
     * @return 대응 enum, 매칭이 없으면 {@code null}(모니터링 대상 외 구간 등)
     */
    public static PredictionDuration fromCode(String code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(d -> d.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}
