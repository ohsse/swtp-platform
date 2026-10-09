package com.mindone.editor.common.controller;

import com.mindone.editor.common.response.ResponseObject;
import org.springframework.http.ResponseEntity;

/**
 * 컨트롤러 공통 베이스.
 *
 * <p>컨트롤러가 이 클래스를 상속하면 성공 응답을 {@link ResponseObject} 형식으로
 * 일관되게 생성할 수 있다. 실패 응답은
 * {@link com.mindone.editor.common.exception.advice.RestApiAdvice} 가 전역으로 처리한다.</p>
 */
public class CommonController {

    /**
     * 데이터를 포함한 성공 응답 생성.
     *
     * @param data 응답 데이터
     * @return code 가 {@code "SUCCESS"} 인 200 응답
     */
    protected <T> ResponseEntity<ResponseObject<T>> getResponseEntity(T data) {
        return ResponseEntity.ok(
                ResponseObject.<T>builder()
                        .code("SUCCESS")
                        .data(data)
                        .build()
        );
    }


    /**
     * 데이터 없는 성공 응답 생성.
     *
     * @return code 가 {@code "SUCCESS"} 인 200 응답
     */
    protected ResponseEntity<ResponseObject<Void>> getResponseEntity() {
        return ResponseEntity.ok(
                ResponseObject.<Void>builder()
                        .code("SUCCESS")
                        .build()
        );
    }
}
