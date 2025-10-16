package com.respiroc.shopifyreaisync.service

import com.respiroc.shopifyreaisync.dto.reai.ReaiTokenResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

@Service
class ReaiTokenClient(
    private val restClientBuilder: RestClient.Builder,
    @Value("\${reai.token-url}")
    private val tokenEndpoint: String
) {
    fun exchangeClientCredentials(clientId: String, clientSecret: String, scopes: List<String>): String {
        val formData = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "client_credentials")
            if (scopes.isNotEmpty()) {
                add("scope", scopes.joinToString(" "))
            }
        }
        val restClient = restClientBuilder.build()
        val response = restClient.post()
            .uri(tokenEndpoint)
            .headers { headers -> headers.setBasicAuth(clientId, clientSecret) }
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(formData)
            .retrieve()
            .body(ReaiTokenResponse::class.java)
            ?: throw IllegalStateException("Token response was empty")
        return response.accessToken ?: throw IllegalStateException("Token response missing access token")
    }
}
