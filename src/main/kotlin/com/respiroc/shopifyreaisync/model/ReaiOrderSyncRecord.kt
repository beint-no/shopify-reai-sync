package com.respiroc.shopifyreaisync.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "reai_order_sync")
class ReaiOrderSyncRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,
    
    @Column(name = "shopify_order_number", nullable = false, length = 128)
    val shopifyOrderNumber: String,
    @Column(name = "tenant_id", nullable = false)
    val tenantId: Long,
    @Column(name = "reai_order_id")
    var reaiOrderId: Long? = null,
    @Column(name = "reai_invoice_id")
    var reaiInvoiceId: Long? = null,
    @Column(name = "reai_invoice_number", length = 128)
    var reaiInvoiceNumber: String? = null,
    @Column(name = "synced_at", nullable = false)
    var syncedAt: OffsetDateTime
)