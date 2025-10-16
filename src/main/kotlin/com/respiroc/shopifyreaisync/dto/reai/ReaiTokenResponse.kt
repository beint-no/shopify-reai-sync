package com.respiroc.shopifyreaisync.dto.reai

import com.fasterxml.jackson.annotation.JsonProperty

data class ReaiTokenResponse(
    @JsonProperty("access_token")
    val accessToken: String?,
    @JsonProperty("token_type")
    val tokenType: String?,
    @JsonProperty("expires_in")
    val expiresIn: Long?,
    val scope: String?
)
