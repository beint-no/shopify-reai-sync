package com.respiroc.shopifyreaisync.dto.reai

import java.math.BigDecimal
import java.time.LocalDate

data class ReaiCustomerResponse(
    val id: Long,
    val name: String,
    val email: String?,
    val type: String,
    val companyId: Long?,
    val personId: Long?
)

data class ReaiCreateCustomerRequest(
    val name: String,
    val organizationNumber: String? = null,
    val privateContact: Boolean,
    val email: String?,
    val countryCode: String?,
    val city: String?,
    val postalCode: String?,
    val administrativeDivisionCode: String?,
    val addressPart1: String?,
    val addressPart2: String?
)

data class ReaiNewOrderRequest(
    val daysUntilDue: Int,
    val currencyCode: String,
    val customerId: Long,
    val orderLines: List<ReaiNewOrderLineRequest>
)

data class ReaiNewOrderLineRequest(
    val rowNumber: Int,
    val itemName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val discount: BigDecimal?,
    val vatCode: String,
    val variantId: Long? = null
)

data class ReaiOrderResponse(
    val id: Long,
    val number: String,
    val currencyCode: String
)

data class ReaiNewInvoiceRequest(
    val issueDate: LocalDate,
    val dueDate: LocalDate?,
    val orderId: Long,
    val comment: String?,
    val email: String?,
    val sendEmail: Boolean?
)

data class ReaiInvoiceResponse(
    val id: Long,
    val number: String,
    val order: ReaiOrderResponse
)

data class ReaiProductSyncRequest(
    val productId: Long?,
    val title: String,
    val description: String?,
    val variantOptionTypes: Set<ReaiVariantOptionType>,
    val variants: List<ReaiProductVariantSyncRequest>
)

data class ReaiProductVariantSyncRequest(
    val sku: String,
    val barcode: String?,
    val costPrice: BigDecimal?,
    val sellingPrice: BigDecimal,
    val options: Map<ReaiVariantOptionType, String>,
    val inventory: Int?,
    val warehouseName: String?
)

data class ReaiProductSyncResponse(
    val productId: Long,
    val variants: List<ReaiProductVariantSyncResponse>
)

data class ReaiProductVariantSyncResponse(
    val variantId: Long,
    val sku: String
)

data class ReaiProductSnapshotResponse(
    val productId: Long,
    val title: String,
    val description: String?,
    val variantOptionTypes: Set<String>,
    val variants: List<ReaiProductVariantSnapshotResponse>
)

data class ReaiProductVariantSnapshotResponse(
    val variantId: Long,
    val sku: String,
    val barcode: String?,
    val costPrice: BigDecimal,
    val sellingPrice: BigDecimal,
    val options: Map<String, String>,
    val inventoryQuantity: Int?,
    val warehouseName: String?
)
