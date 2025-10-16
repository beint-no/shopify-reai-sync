package com.respiroc.shopifyreaisync.dto.order

import com.respiroc.shopifyreaisync.dto.shared.MoneyAmount
import java.time.OffsetDateTime

data class ShopifyOrderDetails(
    val orderName: String,
    val orderNumber: String,
    val createdAt: OffsetDateTime,
    val customerEmail: String?,
    val customerName: String?,
    val fulfillmentStatus: String?,
    val totalPrice: MoneyAmount?,
    val shippingAddress: OrderShippingAddress?,
    val lineItems: List<OrderLineItem>,
    val fulfillments: List<FulfillmentSummary>,
    val dueDate: OffsetDateTime?
)

data class OrderLineItem(
    val title: String,
    val sku: String?,
    val quantity: Int,
    val totalPrice: MoneyAmount?
)

data class FulfillmentSummary(
    val createdAt: OffsetDateTime,
    val status: String?,
    val trackingNumbers: List<String>,
    val trackingUrls: List<String>
)

data class OrderShippingAddress(
    val name: String?,
    val addressLineOne: String?,
    val addressLineTwo: String?,
    val city: String?,
    val province: String?,
    val country: String?,
    val postalCode: String?,
    val phone: String?
)
