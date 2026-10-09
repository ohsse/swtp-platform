package com.mindone.editor.inp;

import com.mindone.editor.common.domain.YesOrNo;
import com.mindone.editor.inp.domain.RevisionWorkType;
import com.mindone.editor.inp.dto.InpFileResponse;
import com.mindone.editor.inp.service.InpFileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 업로드 API 의 Swagger 호환성 검증.
 *
 * <p>Swagger UI 는 멀티파트 객체 파트({@code info})를 {@code application/json} 이 아닌
 * {@code application/octet-stream} 으로 전송한다. {@link com.mindone.editor.common.config.WebConfig} 가
 * Jackson 컨버터에 octet-stream 을 추가해 이 파트를 JSON(UTF-8)으로 읽어내므로, 415 없이 업로드되고
 * 한글 파일명도 보존되어야 한다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class InpFileUploadSwaggerCompatTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InpFileService inpFileService;

    @Test
    @DisplayName("info 파트가 octet-stream 으로 와도 JSON 으로 읽혀 업로드되고 한글이 보존된다")
    void uploadsWhenInfoPartIsOctetStream() throws Exception {
        String koreanName = "테스트관망도.inp";
        given(inpFileService.upload(any(), eq(koreanName)))
                .willReturn(new InpFileResponse(
                        "id-1", koreanName, "id-1_r0.inp", "inp", 3L, 0, RevisionWorkType.ORIGIN, YesOrNo.N, null));

        MockMultipartFile filePart = new MockMultipartFile(
                "file", "upload.inp", MediaType.APPLICATION_OCTET_STREAM_VALUE, "abc".getBytes(StandardCharsets.UTF_8));
        byte[] infoJson = ("{\"orgnlFileNm\":\"" + koreanName + "\"}").getBytes(StandardCharsets.UTF_8);
        // Swagger UI 가 보내는 것과 동일하게 info 파트 Content-Type 을 octet-stream 으로 지정
        MockMultipartFile infoPart = new MockMultipartFile(
                "info", "", MediaType.APPLICATION_OCTET_STREAM_VALUE, infoJson);

        mockMvc.perform(multipart("/inp-files").file(filePart).file(infoPart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.orgnlFileNm").value(koreanName));

        // 서비스에 전달된 파일명이 한글 그대로면 octet-stream → JSON(UTF-8) 디코딩이 정상
        verify(inpFileService).upload(any(), eq(koreanName));
    }
}
