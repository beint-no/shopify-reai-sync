package com.respiroc.shopifyreaisync.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.beans.factory.annotation.Value
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
    private val reaiConnectionRepository: ReaiConnectionRepository
) {

    private val objectMapper = jacksonObjectMapper()

    @Transactional
    fun storeAccessToken(newAccessToken: String, shopDomain: String?): ReaiConnection {
        val jwtDetails = parseJwt(newAccessToken)
        val tenantId = jwtDetails.tenantId ?: throw IllegalArgumentException("Tenant ID not found in JWT")
        val existingConnection = reaiConnectionRepository.findByTenantId(tenantId)
        val installation = if (shopDomain != null) {
            shopifyInstallationService.findByShopDomain(shopDomain)
        } else {
            null
        }

        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val connection = if (existingConnection == null) {
            ReaiConnection(
                shopifyInstallation = installation,
                createdAt = now,
                updatedAt = now,
                tenantId = tenantId,
                accessToken = newAccessToken,
                accessTokenExpiresAt = jwtDetails.expiresAt
            )
        } else {
            existingConnection.apply {
                this.tenantId = tenantId
                accessToken = newAccessToken
                accessTokenExpiresAt = jwtDetails.expiresAt
                updatedAt = now
                if (installation != null) {
                    this.shopifyInstallation = installation
                }
            }
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
        val token = connection.accessToken ?: return false
        if (token.isBlank()) return false
        val expiresAt = connection.accessTokenExpiresAt ?: return true
        return expiresAt.isAfter(OffsetDateTime.now(ZoneOffset.UTC))
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
        val existingConnection = reaiConnectionRepository.findByTenantId(tenantId)
            ?: run {
                throw IllegalArgumentException("ReaiConnection not found for tenantId: $tenantId")
            }

        existingConnection.shopifyInstallation = shopifyInstallation
        existingConnection.updatedAt = OffsetDateTime.now(ZoneOffset.UTC)

        val savedConnection = reaiConnectionRepository.save(existingConnection)
        return savedConnection
    }

    @Transactional
    fun disconnectShopifyInstallation(tenantId: Long): ReaiConnection {
        val existingConnection = reaiConnectionRepository.findByTenantId(tenantId)
            ?: run {
                throw IllegalArgumentException("ReaiConnection not found for tenantId: $tenantId")
            }

        val linkedInstallation = existingConnection.shopifyInstallation
        if (linkedInstallation != null) {
            shopifyInstallationService.deleteInstallation(linkedInstallation)
        }

        existingConnection.shopifyInstallation = null
        existingConnection.updatedAt = OffsetDateTime.now(ZoneOffset.UTC)

        val savedConnection = reaiConnectionRepository.save(existingConnection)
        return savedConnection
    }

    @Transactional(readOnly = true)
    fun findByShopifyInstallation(shopifyInstallation: ShopifyInstallation): ReaiConnection? {
        val connection = reaiConnectionRepository.findByShopifyInstallation(shopifyInstallation)
        return connection
    }
}