package com.mindone.editor.inp.network.writer;

import com.mindone.editor.inp.network.compose.InpDocument;
import com.mindone.editor.inp.network.compose.InpLine;
import com.mindone.editor.inp.network.compose.InpSection;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link InpDocument} 를 INP 텍스트/바이트로 직렬화한다(Write 단계 — 파서/리더의 역).
 *
 * <p>섹션 헤더({@code [NAME]})와 EPANET 컬럼 주석을 출력하고, 데이터 행은 컬럼별 최대폭으로 정렬한다.
 * 가시 객체(절점/관로 등) 행은 EPANET/WNTR 관행대로 줄 끝에 {@code ;}(인라인 주석 자리)를 둔다. 한글은
 * 원본과 동일한 CP949(MS949)로 인코딩한다.</p>
 *
 * <p><b>한계</b>: 상세조회 응답은 손실 투영이라 파일 헤더 주석({@code ; Filename}/{@code ; WNTR}/
 * {@code ; Created})과 원본의 정확한 컬럼 폭/탭/숫자 표기는 복원되지 않는다. 대신 의미적으로 동일하고
 * 재파싱하면 동일한 네트워크가 나오는 정규화된 INP 를 만든다.</p>
 */
@Component
public class InpWriter {

    /** 한글 INP 사실상 표준 인코딩(리더의 폴백 1순위와 동일). */
    private static final Charset MS949 = Charset.forName("MS949");

    /** 줄바꿈(원본과 동일하게 LF). */
    private static final String NL = "\n";

    /** 정렬 시 컬럼 사이 최소 간격(공백 수). */
    private static final int COLUMN_GAP = 3;

    /** 줄 끝에 {@code ;}(빈 인라인 주석)를 두는 가시 객체 섹션. */
    private static final Set<String> OBJECT_SECTIONS = Set.of(
            "JUNCTIONS", "RESERVOIRS", "TANKS", "PIPES", "PUMPS", "VALVES");

    /** 섹션별 EPANET 컬럼 주석(헤더 바로 아래 한 줄). 없으면 생략. */
    private static final Map<String, String> COLUMN_HEADERS = Map.ofEntries(
            Map.entry("JUNCTIONS", ";ID                   Elevation    Demand       Pattern"),
            Map.entry("RESERVOIRS", ";ID                   Head         Pattern"),
            Map.entry("TANKS", ";ID                   Elevation    InitLevel    MinLevel     MaxLevel     Diameter     MinVolume    VolCurve     Overflow"),
            Map.entry("PIPES", ";ID                   Node1                Node2                Length       Diameter     Roughness    MinorLoss    Status"),
            Map.entry("PUMPS", ";ID                   Node1                Node2                Properties"),
            Map.entry("VALVES", ";ID                   Node1                Node2                Diameter     Type         Setting      MinorLoss"),
            Map.entry("TAGS", ";Type      Object               Tag"),
            Map.entry("DEMANDS", ";Junction             Demand       Pattern      Category"),
            Map.entry("STATUS", ";ID                   Status/Setting"),
            Map.entry("PATTERNS", ";ID                   Multipliers"),
            Map.entry("CURVES", ";ID                   X-Value      Y-Value"),
            Map.entry("EMITTERS", ";Junction             Coefficient"),
            Map.entry("QUALITY", ";Node                 InitQuality"),
            Map.entry("SOURCES", ";Node                 Type         Quality      Pattern"),
            Map.entry("REACTIONS", ";Type         Pipe/Tank            Coefficient"),
            Map.entry("MIXING", ";Tank                 Model        Fraction"),
            Map.entry("COORDINATES", ";Node                 X-Coord            Y-Coord"),
            Map.entry("VERTICES", ";Link                 X-Coord            Y-Coord"),
            Map.entry("LABELS", ";X-Coord            Y-Coord            Label & Anchor Node")
    );

    /**
     * INP 문서를 텍스트로 직렬화한다.
     *
     * @param doc 전 섹션이 채워진 INP 문서
     * @return INP 텍스트
     */
    public String write(InpDocument doc) {
        StringBuilder sb = new StringBuilder(1 << 20);
        for (InpSection section : doc.sections()) {
            writeSection(sb, section);
        }
        return sb.toString();
    }

    /**
     * INP 문서를 CP949(MS949) 바이트로 직렬화한다.
     *
     * @param doc 전 섹션이 채워진 INP 문서
     * @return CP949 인코딩 바이트
     */
    public byte[] writeBytes(InpDocument doc) {
        return write(doc).getBytes(MS949);
    }

    /** 인코딩에 사용한 문자셋 이름(메타 노출용). */
    public String charsetName() {
        return MS949.name();
    }

    /** 한 섹션을 출력한다: 헤더 → 컬럼 주석 → 데이터 라인 → 빈 줄. */
    private void writeSection(StringBuilder sb, InpSection section) {
        sb.append('[').append(section.name()).append(']').append(NL);

        String header = COLUMN_HEADERS.get(section.name());
        if (header != null) {
            sb.append(header).append(NL);
        }

        int[] widths = columnWidths(section);
        boolean objectSection = OBJECT_SECTIONS.contains(section.name());
        for (InpLine line : section.lines()) {
            if (line.isRaw()) {
                sb.append(line.raw()).append(NL);
            } else {
                sb.append(renderTokens(line, widths, objectSection)).append(NL);
            }
        }
        sb.append(NL); // 섹션 사이 빈 줄
    }

    /** 토큰 라인을 컬럼 정렬해 렌더링하고(마지막 토큰은 패딩 없음) 주석/줄끝 {@code ;} 를 붙인다. */
    private String renderTokens(InpLine line, int[] widths, boolean objectSection) {
        List<String> tokens = line.tokens();
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (i < tokens.size() - 1) {
                row.append(pad(token, widths[i]));
            } else {
                row.append(token); // 마지막 토큰은 패딩하지 않는다
            }
        }
        appendComment(row, line.comment(), objectSection);
        return row.toString();
    }

    /** 인라인 주석을 붙인다. 주석이 있으면 {@code ; 주석}, 없고 가시 객체면 빈 {@code ;}. */
    private void appendComment(StringBuilder row, String comment, boolean objectSection) {
        if (comment != null && !comment.isEmpty()) {
            row.append("  ;").append(comment);
        } else if (objectSection) {
            row.append("  ;");
        }
    }

    /** 섹션 내 토큰 라인들의 컬럼별 최대 폭 + 간격을 계산한다(마지막 컬럼은 사용 안 함). */
    private int[] columnWidths(InpSection section) {
        int maxCols = 0;
        for (InpLine line : section.lines()) {
            if (!line.isRaw()) {
                maxCols = Math.max(maxCols, line.tokens().size());
            }
        }
        int[] widths = new int[Math.max(maxCols, 1)];
        for (InpLine line : section.lines()) {
            if (line.isRaw()) {
                continue;
            }
            List<String> tokens = line.tokens();
            for (int i = 0; i < tokens.size(); i++) {
                widths[i] = Math.max(widths[i], length(tokens.get(i)) + COLUMN_GAP);
            }
        }
        return widths;
    }

    /** 토큰을 지정 폭으로 좌측 정렬 패딩한다(폭보다 길면 한 칸 띄운다). */
    private String pad(String token, int width) {
        int len = length(token);
        if (len >= width) {
            return token + " ";
        }
        StringBuilder sb = new StringBuilder(token);
        for (int i = len; i < width; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /** 정렬 폭 계산용 길이(null 은 0). */
    private int length(String s) {
        return s == null ? 0 : s.length();
    }
}
