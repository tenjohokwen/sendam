package com.softropic.sendam.common.message;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
public record ErrorMsg(String errorKey, String message) {
}
