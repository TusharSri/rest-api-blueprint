package io.github.tusharsri.blueprint.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
