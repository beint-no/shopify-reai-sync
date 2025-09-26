package com.respiroc.shopifyreaisync.oauth

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@Component
class ShopifyOAuthStateStore {
    private val stateCache = ConcurrentHashMap<String, ShopifyOAuthState>()
    private val secureRandom = SecureRandom()
    private val stateLifetime = Duration.ofMinutes(15)

    fun issueState(shopDomain: String): String {
        val randomBytes = ByteArray(24)
        secureRandom.nextBytes(randomBytes)
        val stateValue = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
        val issuedState = ShopifyOAuthState(
            value = stateValue,
            shopDomain = shopDomain.lowercase(),
            createdAt = OffsetDateTime.now(ZoneOffset.UTC)
        )
        stateCache[stateValue] = issuedState
        evictExpiredStates()
        return stateValue
    }

    fun consumeState(value: String, shopDomain: String): Boolean {
        evictExpiredStates()
        val storedState = stateCache.remove(value) ?: return false
        return storedState.shopDomain == shopDomain.lowercase()
    }

    private fun evictExpiredStates() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        stateCache.entries.removeIf { now.isAfter(it.value.createdAt.plus(stateLifetime)) }
    }

    private data class ShopifyOAuthState(
        val value: String,
        val shopDomain: String,
        val createdAt: OffsetDateTime
    )
}
