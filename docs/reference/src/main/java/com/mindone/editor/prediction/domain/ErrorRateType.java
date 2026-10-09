package com.mindone.editor.prediction.domain;

/**
 * 적중률 산출 기준 오차율.
 *
 * <p>적중률(%) = 100 − 오차율 에서 어떤 오차율을 쓸지 선택한다. sMAPE 가 왜곡이 적어 기본값으로 둔다.</p>
 */
public enum ErrorRateType {
    /** 오차율1: MAPE. */
    MAPE,
    /** 오차율2: sMAPE (기본, 왜곡 적음). */
    SMAPE
}
