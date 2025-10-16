package com.respiroc.shopifyreaisync.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "reai_connection")
class ReaiConnection(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shopify_installation_id")
    var shopifyInstallation: ShopifyInstallation? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime,
    @Column(name = "tenant_id")
    var tenantId: Long? = null,
    @Column(name = "access_token", columnDefinition = "text")
    var accessToken: String? = null,
    @Column(name = "access_token_expires_at")
    var accessTokenExpiresAt: OffsetDateTime? = null,
    @Column(name = "client_id", length = 160)
    var clientId: String? = null,
    @Column(name = "client_secret", columnDefinition = "text")
    var clientSecret: String? = null,
    @Column(name = "granted_scope", length = 512)
    var grantedScope: String? = null,
    @Column(name = "auto_sync", nullable = false)
    var autoSync: Boolean = false
)
