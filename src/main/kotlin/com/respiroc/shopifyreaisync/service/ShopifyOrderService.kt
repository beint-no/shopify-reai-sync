package com.respiroc.shopifyreaisync.service

import com.apollographql.apollo.ApolloClient
import com.respiroc.shopifyreaisync.config.ShopifyProperties
import com.respiroc.shopifyreaisync.graphql.GetOrderByNameQuery
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

@Service
class ShopifyOrderService(
    private val shopifyInstallationService: ShopifyInstallationService,
    private val shopifyProperties: ShopifyProperties
) {
    fun fetchOrderByNumber(shopDomain: String, rawOrderNumber: String): ShopifyOrderDetails {
        val installation = shopifyInstallationService.findByShopDomain(shopDomain)
            ?: throw IllegalArgumentException("Shop installation not found for $shopDomain")
        val sanitizedNumber = rawOrderNumber.trim().removePrefix("#")
        if (sanitizedNumber.isBlank()) {
            throw IllegalArgumentException("Order number is required")
        }
        val filterValue = "name:\"${escapeGraphqlValue(sanitizedNumber)}\""
        val apolloClient = ApolloClient.Builder()
            .serverUrl("https://${installation.shopDomain}/admin/api/${shopifyProperties.apiVersion}/graphql.json")
            .addHttpHeader("X-Shopify-Access-Token", installation.accessToken)
            .build()
        val response = runBlocking {
            apolloClient.query(GetOrderByNameQuery(filterValue)).execute()
        }
        if (!response.errors.isNullOrEmpty()) {
            val aggregatedMessage = response.errors!!.joinToString(",") { it.message }
            throw IllegalStateException("Shopify returned errors: $aggregatedMessage")
        }
        val orderNode = response.data?.orders?.edges?.firstOrNull()?.node
            ?: throw OrderNotFoundException(rawOrderNumber)
        return mapToOrderDetails(orderNode)
    }

    private fun mapToOrderDetails(orderNode: GetOrderByNameQuery.Node): ShopifyOrderDetails {
        val shopMoney = orderNode.currentTotalPriceSet.shopMoney
        val orderTotal = shopMoney.amount.toString().toBigDecimalOrNull()?.let {
            MoneyAmount(
                amount = it,
                currencyCode = shopMoney.currencyCode.rawValue
            )
        }
        val shippingAddress = orderNode.shippingAddress?.let {
            OrderShippingAddress(
                name = listOfNotNull(it.firstName, it.lastName).joinToString(" ").ifBlank { null },
                addressLineOne = it.address1,
                addressLineTwo = it.address2,
                city = it.city,
                province = it.province,
                country = it.country,
                postalCode = it.zip,
                phone = it.phone
            )
        }
        val lineItems = orderNode.lineItems.nodes.map { node ->
            val discountedMoney = node.discountedTotalSet.shopMoney
            val totalMoney = discountedMoney.amount.toString().toBigDecimalOrNull()?.let {
                MoneyAmount(amount = it, currencyCode = discountedMoney.currencyCode.rawValue)
            }
            OrderLineItem(
                title = node.title,
                sku = node.sku,
                quantity = node.quantity,
                totalPrice = totalMoney
            )
        }
        val fulfillments = orderNode.fulfillments.map { node ->
            FulfillmentSummary(
                createdAt = node.createdAt.asOffsetDateTime(),
                status = node.displayStatus?.rawValue,
                trackingNumbers = node.trackingInfo.mapNotNull { it.number }.filter { it.isNotBlank() },
                trackingUrls = node.trackingInfo.mapNotNull { it.url?.toString() }.filter { it.isNotBlank() }
            )
        }
        return ShopifyOrderDetails(
            orderName = orderNode.name,
            orderNumber = orderNode.name.removePrefix("#"),
            createdAt = orderNode.createdAt.asOffsetDateTime(),
            customerEmail = orderNode.email,
            customerName = orderNode.customer?.displayName,
            fulfillmentStatus = orderNode.displayFulfillmentStatus.rawValue,
            totalPrice = orderTotal,
            shippingAddress = shippingAddress,
            lineItems = lineItems,
            fulfillments = fulfillments
        )
    }

    private fun escapeGraphqlValue(value: String): String {
        val builder = StringBuilder()
        value.forEach { character ->
            when (character) {
                '\\' -> builder.append("\\\\")
                '"' -> builder.append("\\\"")
                else -> builder.append(character)
            }
        }
        return builder.toString()
    }
}

private fun Any.asOffsetDateTime(): OffsetDateTime {
    return when (this) {
        is OffsetDateTime -> this
        is String -> try {
            OffsetDateTime.parse(this)
        } catch (exception: DateTimeParseException) {
            throw IllegalStateException("Unable to parse date: $this", exception)
        }
        else -> throw IllegalStateException("Unsupported date value: $this")
    }
}

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
    val fulfillments: List<FulfillmentSummary>
)

data class MoneyAmount(
    val amount: BigDecimal,
    val currencyCode: String
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

class OrderNotFoundException(orderNumber: String) : RuntimeException("Order $orderNumber not found")
