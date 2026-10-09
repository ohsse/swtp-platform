package com.mindone.editor.inp.network.intake;

import lombok.extern.slf4j.Slf4j;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * INP 파일 바이트를 인코딩 자동판별 후 텍스트 라인으로 디코딩하는 컴포넌트(File Intake 단계).
 *
 * <p>국내 INP 파일은 대부분 CP949(MS949)로 저장되지만 UTF-8 파일도 있을 수 있어, 바이트
 * 시그니처 기반으로 문자셋을 자동판별({@code juniversalchardet})한 뒤 디코딩한다. 판별이
 * 불확실하면 한글 보존에 안전한 순서(MS949 → UTF-8)로 폴백한다.</p>
 */
@Slf4j
@Component
public class InpFileReader {

    /** 한글 INP 의 사실상 표준 인코딩. EUC-KR 의 상위호환(확장 완성형 포함)이라 폴백 1순위로 둔다. */
    private static final Charset MS949 = Charset.forName("MS949");

    /** UTF-8 BOM (EF BB BF). */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /**
     * INP 파일 바이트를 라인 목록으로 디코딩한다.
     *
     * @param bytes INP 파일 전체 바이트
     * @return 디코딩된 라인 목록과 사용된 문자셋 이름
     */
    public InpReadResult read(byte[] bytes) {
        // 1. BOM 우선 처리 (UTF-8 BOM 이 있으면 UTF-8 확정 + BOM 제거)
        if (hasUtf8Bom(bytes)) {
            String text = new String(bytes, UTF8_BOM.length, bytes.length - UTF8_BOM.length, StandardCharsets.UTF_8);
            log.debug("INP 인코딩: UTF-8 (BOM)");
            return new InpReadResult(splitLines(text), StandardCharsets.UTF_8.name());
        }

        // 2. 자동판별 → 사용할 문자셋 결정
        Charset charset = resolveCharset(detect(bytes), bytes);
        log.debug("INP 인코딩: {}", charset);
        return new InpReadResult(splitLines(new String(bytes, charset)), charset.name());
    }

    /** {@code juniversalchardet} 로 문자셋 이름을 추정한다. 판별 실패 시 {@code null}. */
    private String detect(byte[] bytes) {
        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(bytes, 0, bytes.length);
        detector.dataEnd();
        String detected = detector.getDetectedCharset();
        detector.reset();
        return detected;
    }

    /**
     * 판별 결과를 실제 사용할 {@link Charset} 으로 변환한다.
     *
     * <ul>
     *     <li>판별 실패 → MS949 (국내 INP 다수가 CP949)</li>
     *     <li>EUC-KR 판별 → MS949 로 승격 (확장 완성형 한글까지 안전 디코딩)</li>
     *     <li>그 외(UTF-8 등) → 판별값 사용, 미지원 이름이면 MS949 폴백</li>
     * </ul>
     */
    private Charset resolveCharset(String detected, byte[] bytes) {
        if (detected == null || detected.isBlank()) {
            return MS949;
        }
        if (detected.equalsIgnoreCase("EUC-KR")) {
            return MS949;
        }
        try {
            return Charset.forName(detected);
        } catch (Exception e) {
            log.warn("판별된 문자셋[{}] 미지원 → MS949 폴백", detected);
            return MS949;
        }
    }

    /** UTF-8 BOM 으로 시작하는지 검사한다. */
    private boolean hasUtf8Bom(byte[] bytes) {
        if (bytes.length < UTF8_BOM.length) {
            return false;
        }
        for (int i = 0; i < UTF8_BOM.length; i++) {
            if (bytes[i] != UTF8_BOM[i]) {
                return false;
            }
        }
        return true;
    }

    /** 텍스트를 개행 기준으로 분할한다(CRLF/CR/LF 모두 처리, 개행 문자 제거). */
    private List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            // StringReader 기반이라 실질적으로 발생하지 않는다.
            throw new IllegalStateException("INP 라인 분할 실패", e);
        }
        return lines;
    }
}
