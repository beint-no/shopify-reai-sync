package com.respiroc.shopifyreaisync.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.respiroc.shopifyreaisync.dto.store.ShopifyDisconnectRequest
import com.respiroc.shopifyreaisync.model.ReaiConnection
import com.respiroc.shopifyreaisync.model.ShopifyInstallation
import com.respiroc.shopifyreaisync.repository.ReaiConnectionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64

@Service
class ReaiConnectionService(
    private val shopifyInstallationService: ShopifyInstallationService,
    private val reaiConnectionRepository: ReaiConnectionRepository,
    private val reaiTokenClient: ReaiTokenClient
) {

    private val objectMapper = jacksonObjectMapper()

    @Transactional
    fun storeAccessToken(
        newAccessToken: String,
        shopDomain: String?,
        clientId: String? = null,
        clientSecret: String? = null,
        scope: String? = null
    ): ReaiConnection {
        val jwtDetails = parseJwt(newAccessToken)
        val tenantId = jwtDetails.tenantId ?: throw IllegalArgumentException("Tenant ID not found in JWT")
        val existingConnections = reaiConnectionRepository.findByTenantId(tenantId)
        val installation = if (shopDomain != null) {
            shopifyInstallationService.findByShopDomain(shopDomain)
        } else {
            null
        }

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val normalizedClientId = clientId?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedClientSecret = clientSecret?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedScope = normalizeScope(scope)

        val connection = if (installation != null) {
            existingConnections.find { it.shopifyInstallation?.id == installation.id }?.apply {
                this.tenantId = tenantId
                accessToken = newAccessToken
                accessTokenExpiresAt = jwtDetails.expiresAt
                updatedAt = now
                if (normalizedClientId != null) {
                    this.clientId = normalizedClientId
                }
                if (normalizedClientSecret != null) {
                    this.clientSecret = normalizedClientSecret
                }
                if (normalizedScope != null) {
                    grantedScope = normalizedScope
                }
            } ?: ReaiConnection(
                shopifyInstallation = installation,
                createdAt = now,
                updatedAt = now,
                tenantId = tenantId,
                accessToken = newAccessToken,
                accessTokenExpiresAt = jwtDetails.expiresAt,
                clientId = normalizedClientId,
                clientSecret = normalizedClientSecret,
                grantedScope = normalizedScope
            )
        } else {
            existingConnections.firstOrNull()?.apply {
                this.tenantId = tenantId
                accessToken = newAccessToken
                accessTokenExpiresAt = jwtDetails.expiresAt
                updatedAt = now
                if (normalizedClientId != null) {
                    this.clientId = normalizedClientId
                }
                if (normalizedClientSecret != null) {
                    this.clientSecret = normalizedClientSecret
                }
                if (normalizedScope != null) {
                    grantedScope = normalizedScope
                }
            } ?: ReaiConnection(
                createdAt = now,
                updatedAt = now,
                tenantId = tenantId,
                accessToken = newAccessToken,
                accessTokenExpiresAt = jwtDetails.expiresAt,
                clientId = normalizedClientId,
                clientSecret = normalizedClientSecret,
                grantedScope = normalizedScope
            )
        }

        val savedConnection = reaiConnectionRepository.save(connection)
        return savedConnection
    }


    @Transactional(readOnly = true)
    fun findByShopDomainAndTenantId(shopDomain: String, tenantId: Long): ReaiConnection? {
        val installation = shopifyInstallationService.findByShopDomainAndTenantId(shopDomain, tenantId) ?: return null
        return reaiConnectionRepository.findByShopifyInstallation(installation)
    }


    fun hasValidToken(connection: ReaiConnection?): Boolean {
        if (connection == null) return false
        val token = fetchValidAccessToken(connection)
        return !token.isNullOrBlank()
    }

    @Transactional
    fun ensureValidAccessToken(connection: ReaiConnection): String {
        val token = connection.accessToken
            ?: throw IllegalStateException("ReAI access token missing. Open this app from ReAI to establish the connection.")
        val expiry = connection.accessTokenExpiresAt
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        if (expiry == null || expiry.isAfter(now.plusSeconds(30))) {
            return token
        }
        return refreshAccessToken(connection)
    }

    fun fetchValidAccessToken(connection: ReaiConnection?): String? {
        if (connection == null) return null
        return try {
            ensureValidAccessToken(connection)
        } catch (ignored: IllegalStateException) {
            null
        }
    }

    @Transactional
    fun refreshAccessToken(connection: ReaiConnection): String {
        val normalizedClientId = connection.clientId?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("ReAI client credentials missing. Open this app from ReAI to establish the connection again.")
        val normalizedClientSecret = connection.clientSecret?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("ReAI client credentials missing. Open this app from ReAI to establish the connection again.")
        val scopeTokens = connection.grantedScope
            ?.split(' ', ',', '\n', '\t')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        val refreshedToken = reaiTokenClient.exchangeClientCredentials(normalizedClientId, normalizedClientSecret, scopeTokens)
        val jwtDetails = parseJwt(refreshedToken)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        connection.accessToken = refreshedToken
        connection.accessTokenExpiresAt = jwtDetails.expiresAt
        connection.updatedAt = now
        connection.clientId = normalizedClientId
        connection.clientSecret = normalizedClientSecret
        connection.grantedScope = scopeTokens.takeIf { it.isNotEmpty() }?.joinToString(" ")
        if (jwtDetails.tenantId != null) {
            connection.tenantId = jwtDetails.tenantId
        }
        val persisted = if (connection.id != null) {
            reaiConnectionRepository.save(connection)
        } else {
            connection
        }
        return persisted.accessToken
            ?: throw IllegalStateException("Failed to refresh ReAI access token.")
    }

    private fun parseJwt(token: String): JwtDetails {
        return try {
            val parts = token.split('.')
            if (parts.size < 2) return JwtDetails()
            val payloadBytes = Base64.getUrlDecoder().decode(parts[1])
            val payloadJson = String(payloadBytes, StandardCharsets.UTF_8)
            val payloadNode: JsonNode = objectMapper.readTree(payloadJson)
            val exp = payloadNode.get("exp")?.asLong()
            val tenantId = payloadNode.get("tenantId")?.asLong()
            JwtDetails(
                expiresAt = exp?.let { OffsetDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneOffset.UTC) },
                tenantId = tenantId
            )
        } catch (ignored: Exception) {
            JwtDetails()
        }
    }

    private data class JwtDetails(
        val expiresAt: OffsetDateTime? = null,
        val tenantId: Long? = null
    )

    @Transactional
    fun linkShopifyInstallation(tenantId: Long, shopifyInstallation: ShopifyInstallation): ReaiConnection {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val newConnection = ReaiConnection(
            tenantId = tenantId,
            shopifyInstallation = shopifyInstallation,
            createdAt = now,
            updatedAt = now
        )
        return reaiConnectionRepository.save(newConnection)
    }

    @Transactional
    fun disconnectShopifyInstallation(request: ShopifyDisconnectRequest) {
        val installation = shopifyInstallationService.findByShopDomainAndTenantId(request.shopDomain, request.tenantId)
            ?: throw IllegalArgumentException("Shopify installation not found for domain: ${request.shopDomain} and tenant: ${request.tenantId}")

        val connection = reaiConnectionRepository.findByShopifyInstallation(installation)
            ?: throw IllegalArgumentException("ReAI connection not found for installation ID: ${installation.id}")

        reaiConnectionRepository.delete(connection)
        shopifyInstallationService.deleteInstallation(installation)
    }

    @Transactional
    fun toggleAutoSync(tenantId: Long, shopDomain: String) {
        val connection = findByShopDomainAndTenantId(shopDomain, tenantId)
            ?: throw IllegalArgumentException("ReAI connection not found for shop: $shopDomain and tenant: $tenantId")
        connection.autoSync = !connection.autoSync
        reaiConnectionRepository.save(connection)
    }

    @Transactional(readOnly = true)
    fun findByShopifyInstallation(shopifyInstallation: ShopifyInstallation): ReaiConnection? {
        val connection = reaiConnectionRepository.findByShopifyInstallation(shopifyInstallation)
        return connection
    }

    private fun normalizeScope(scope: String?): String? {
        return scope
            ?.split(' ', ',', '\n', '\t')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(" ")
    }
}
