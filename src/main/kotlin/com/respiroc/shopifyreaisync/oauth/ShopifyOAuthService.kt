package com.respiroc.shopifyreaisync.oauth

import com.fasterxml.jackson.annotation.JsonProperty
import com.respiroc.shopifyreaisync.config.ShopifyProperties
import com.respiroc.shopifyreaisync.model.ShopifyInstallation
import com.respiroc.shopifyreaisync.service.ReaiConnectionService
import com.respiroc.shopifyreaisync.service.ShopifyInstallationService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.HexFormat

@Service
class ShopifyOAuthService(
    private val shopifyProperties: ShopifyProperties,
    private val shopifyInstallationService: ShopifyInstallationService,
    private val shopifyOAuthStateStore: ShopifyOAuthStateStore,
    private val restClientBuilder: RestClient.Builder,
    private val reaiConnectionService: ReaiConnectionService
) {
    fun buildInstallationRedirect(shopDomain: String, tenantId: Long?): URI {
        val normalizedDomain = shopDomain.lowercase()
        val state = shopifyOAuthStateStore.issueState(normalizedDomain, tenantId)
        val encodedRedirectUri = URLEncoder.encode("${shopifyProperties.appBaseUrl}/oauth/callback", StandardCharsets.UTF_8)
        val authorizationUrl = StringBuilder()
        authorizationUrl.append("https://")
        authorizationUrl.append(normalizedDomain)
        authorizationUrl.append("/admin/oauth/authorize")
        authorizationUrl.append("?client_id=")
        authorizationUrl.append(shopifyProperties.apiKey)
        authorizationUrl.append("&scope=")
        authorizationUrl.append(URLEncoder.encode(shopifyProperties.scopes, StandardCharsets.UTF_8))
        authorizationUrl.append("&redirect_uri=")
        authorizationUrl.append(encodedRedirectUri)
        authorizationUrl.append("&state=")
        authorizationUrl.append(state)
        return URI.create(authorizationUrl.toString())
    }

    fun handleCallback(parameters: Map<String, String>): ShopifyInstallation {
        val hmac = parameters["hmac"] ?: throw IllegalArgumentException("Missing hmac")
        val shopDomain = parameters["shop"] ?: throw IllegalArgumentException("Missing shop")
        val state = parameters["state"] ?: throw IllegalArgumentException("Missing state")

        val storedState = shopifyOAuthStateStore.consumeState(state, shopDomain)
            ?: throw IllegalArgumentException("Invalid state or state expired for shop: $shopDomain")


        if (!isValidHmac(parameters, hmac)) {
            throw IllegalArgumentException("Invalid hmac")
        }
        val code = parameters["code"] ?: throw IllegalArgumentException("Missing code")
        val tokenResponse = exchangeCodeForToken(shopDomain, code)
        val timestamp = parameters["timestamp"]?.toLongOrNull() ?: throw IllegalArgumentException("Missing timestamp")
        if (Instant.ofEpochSecond(timestamp).isAfter(Instant.now().plusSeconds(300))) {
            throw IllegalArgumentException("Callback timestamp is invalid")
        }
        val installation = shopifyInstallationService.persistInstallation(
            shopDomain = shopDomain,
            accessToken = tokenResponse.accessToken,
            scopes = tokenResponse.scopes,
            tenantId = storedState.tenantId
        )

        val tenantId = storedState.tenantId
        if (tenantId != null) {
            reaiConnectionService.linkShopifyInstallation(tenantId, installation)
        }

        return installation
    }

    private fun exchangeCodeForToken(shopDomain: String, code: String): AccessTokenResponse {
        val requestBody = AccessTokenRequest(
            clientId = shopifyProperties.apiKey,
            clientSecret = shopifyProperties.apiSecret,
            code = code
        )
        val restClient = restClientBuilder.baseUrl("https://$shopDomain").build()
        return restClient.post()
            .uri("/admin/oauth/access_token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(AccessTokenResponse::class.java)
            ?: throw IllegalStateException("Failed to retrieve access token")
    }

    private fun isValidHmac(parameters: Map<String, String>, hmac: String): Boolean {
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(shopifyProperties.apiSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        }

        val message = parameters
            .asSequence()
            .filter { (k, _) -> k != "hmac" && k != "signature" }
            .sortedBy { it.key }
            .joinToString("&") { (k, v) -> "$k=$v" }

        val computed = mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
        val provided = HexFormat.of().parseHex(hmac)
        return MessageDigest.isEqual(computed, provided)
    }

    fun extractParameters(request: HttpServletRequest): Map<String, String> {
        return request.parameterMap.mapValues { it.value.first() }
    }

    private fun hexToBytes(value: String): ByteArray {
        val cleanValue = value.trim()
        require(cleanValue.length % 2 == 0) { "Invalid hex value" }
        val result = ByteArray(cleanValue.length / 2)
        var index = 0
        while (index < cleanValue.length) {
            val byte = cleanValue.substring(index, index + 2).toInt(16)
            result[index / 2] = byte.toByte()
            index += 2
        }
        return result
    }

    private data class AccessTokenRequest(
        @JsonProperty("client_id")
        val clientId: String,
        @JsonProperty("client_secret")
        val clientSecret: String,
        val code: String
    )

    private data class AccessTokenResponse(
        @JsonProperty("access_token")
        val accessToken: String,
        val scope: String
    ) {
        val scopes: String
            get() = scope
    }
}