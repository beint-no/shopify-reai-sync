package com.respiroc.shopifyreaisync.service

import com.respiroc.shopifyreaisync.dto.order.OrderSearchRequest
import com.respiroc.shopifyreaisync.dto.order.OrderSyncRequest
import com.respiroc.shopifyreaisync.dto.order.ReaiSyncResult
import com.respiroc.shopifyreaisync.dto.order.ReaiSyncStatus
import com.respiroc.shopifyreaisync.dto.order.ShopifyOrderDetails
import com.respiroc.shopifyreaisync.dto.reai.ReaiCreateCustomerRequest
import com.respiroc.shopifyreaisync.dto.reai.ReaiCustomerResponse
import com.respiroc.shopifyreaisync.dto.reai.ReaiNewInvoiceRequest
import com.respiroc.shopifyreaisync.dto.reai.ReaiNewOrderLineRequest
import com.respiroc.shopifyreaisync.dto.reai.ReaiNewOrderRequest
import com.respiroc.shopifyreaisync.model.ReaiOrderSyncRecord
import com.respiroc.shopifyreaisync.service.ReaiOrderSyncService.CountryNormalizer.normalize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientResponseException
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.temporal.ChronoUnit
import java.util.Locale

@Service
class ReaiOrderSyncService(
    private val shopifyOrderService: ShopifyOrderService,
    private val reaiConnectionService: ReaiConnectionService,
    private val reaiApiClient: ReaiApiClient,
    private val reaiOrderSyncRecordService: ReaiOrderSyncRecordService
) {
    @Transactional
    fun syncOrder(request: OrderSyncRequest): ReaiSyncResult {
        val orderDetails = shopifyOrderService.fetchOrderByNumber(
            OrderSearchRequest(
                shopDomain = request.shopDomain,
                orderNumber = request.orderNumber,
                tenantId = request.tenantId
            )
        )
        val connection = reaiConnectionService.findByShopDomainAndTenantId(request.shopDomain, request.tenantId)
            ?: throw IllegalStateException("ReAI connection not configured for ${request.shopDomain}")
        val existingRecord = reaiOrderSyncRecordService.findByShopifyOrderNumberAndTenantId(orderDetails.orderNumber, connection.tenantId!!)
        if (existingRecord?.reaiInvoiceId != null && existingRecord.reaiInvoiceNumber != null) {
            return ReaiSyncResult(orderDetails = orderDetails, status = existingRecord.toStatus(true))
        }
        var accessToken = reaiConnectionService.ensureValidAccessToken(connection)
        val customer = try {
            resolveCustomer(accessToken, orderDetails)
        } catch (unauthorized: HttpClientErrorException.Unauthorized) {
            accessToken = reaiConnectionService.refreshAccessToken(connection)
            resolveCustomer(accessToken, orderDetails)
        }
        val orderRequest = buildOrderRequest(orderDetails, customer.id)
        val createdOrder = try {
            reaiApiClient.createOrder(accessToken, orderRequest)
        } catch (unauthorized: HttpClientErrorException.Unauthorized) {
            accessToken = reaiConnectionService.refreshAccessToken(connection)
            reaiApiClient.createOrder(accessToken, orderRequest)
        }
        val invoiceRequest = buildInvoiceRequest(orderDetails, createdOrder.id)
        val invoice = try {
            reaiApiClient.createInvoice(accessToken, invoiceRequest)
        } catch (unauthorized: HttpClientErrorException.Unauthorized) {
            accessToken = reaiConnectionService.refreshAccessToken(connection)
            reaiApiClient.createInvoice(accessToken, invoiceRequest)
        }
        val savedRecord = reaiOrderSyncRecordService.saveSyncResult(
            orderNumber = orderDetails.orderNumber,
            tenantId = connection.tenantId!!,
            reaiOrderId = createdOrder.id,
            reaiInvoiceId = invoice.id,
            reaiInvoiceNumber = invoice.number
        )
        return ReaiSyncResult(orderDetails = orderDetails, status = savedRecord.toStatus(false))
    }

    @Transactional(readOnly = true)
    fun findSyncRecord(request: OrderSearchRequest): ReaiSyncStatus? {
        return reaiOrderSyncRecordService.findByShopifyOrderNumberAndTenantId(request.orderNumber, request.tenantId)?.toStatus(true)
    }

    private fun resolveCustomer(
        token: String,
        orderDetails: ShopifyOrderDetails
    ): ReaiCustomerResponse {
        val candidateName = orderDetails.customerName
            ?: orderDetails.shippingAddress?.name
            ?: "Shopify order ${orderDetails.orderNumber}"
        val searchResults = reaiApiClient.searchCustomers(token, candidateName)
        val preferredCustomer = matchCustomerByEmail(searchResults, orderDetails.customerEmail)
            ?: searchResults.maxByOrNull { it.id }
        if (preferredCustomer != null) {
            return preferredCustomer
        }
        val shippingAddress = orderDetails.shippingAddress
        val createRequest = ReaiCreateCustomerRequest(
            name = candidateName,
            privateContact = true,
            email = orderDetails.customerEmail,
            countryCode = toIsoCountryCode(shippingAddress?.country),
            city = shippingAddress?.city,
            postalCode = shippingAddress?.postalCode,
            administrativeDivisionCode = shippingAddress?.province,
            addressPart1 = shippingAddress?.addressLineOne,
            addressPart2 = shippingAddress?.addressLineTwo
        )
        return try {
            reaiApiClient.createCustomer(token, createRequest)
        } catch (exception: RestClientResponseException) {
            val details = exception.responseBodyAsString.takeIf { it.isNotBlank() }
            val message = buildString {
                append("ReAI rejected customer creation for ")
                append(candidateName)
                append(" (status ${exception.statusCode.value()})")
                if (details != null) {
                    append(": ")
                    append(details)
                }
            }
            throw IllegalStateException(message, exception)
        }
    }

    private fun matchCustomerByEmail(candidates: List<ReaiCustomerResponse>, email: String?): ReaiCustomerResponse? {
        if (email.isNullOrBlank()) return null
        return candidates.firstOrNull { it.email.equals(email, ignoreCase = true) }
    }

    private fun buildOrderRequest(
        orderDetails: ShopifyOrderDetails,
        customerId: Long
    ): ReaiNewOrderRequest {
        val lineRequests = orderDetails.lineItems.mapIndexedNotNull { index, item ->
            val quantity = item.quantity
            val totalPrice = item.totalPrice?.amount ?: return@mapIndexedNotNull null
            if (quantity == 0) return@mapIndexedNotNull null
            val unitPrice = totalPrice.divide(BigDecimal(quantity), 2, RoundingMode.HALF_UP)
            ReaiNewOrderLineRequest(
                rowNumber = index + 1,
                itemName = item.title.ifBlank { "Line ${index + 1}" },
                quantity = quantity,
                unitPrice = unitPrice,
                discount = null,
                vatCode = "0"
            )
        }.toMutableList()
        val shippingAmount = calculateShippingAmount(orderDetails)
        if (shippingAmount != null && shippingAmount > BigDecimal.ZERO) {
            lineRequests.add(
                ReaiNewOrderLineRequest(
                    rowNumber = lineRequests.size + 1,
                    itemName = "Shipping",
                    quantity = 1,
                    unitPrice = shippingAmount.setScale(2, RoundingMode.HALF_UP),
                    discount = null,
                    vatCode = "0"
                )
            )
        }
        if (lineRequests.isEmpty()) {
            throw IllegalStateException("No purchasable line items found for order ${orderDetails.orderNumber}")
        }
        return ReaiNewOrderRequest(
            daysUntilDue = orderDetails.dueDate?.let {
                ChronoUnit.DAYS.between(orderDetails.createdAt.toLocalDate(), it.toLocalDate()).toInt()
            } ?: 0,
            currencyCode = orderDetails.totalPrice?.currencyCode ?: "NOK",
            customerId = customerId,
            orderLines = lineRequests
        )
    }

    private fun buildInvoiceRequest(
        orderDetails: ShopifyOrderDetails,
        orderId: Long
    ): ReaiNewInvoiceRequest {
        val issueDate = orderDetails.createdAt.toLocalDate()
        val dueDate = orderDetails.dueDate?.toLocalDate() ?: issueDate
        return ReaiNewInvoiceRequest(
            issueDate = issueDate,
            dueDate = dueDate,
            orderId = orderId,
            comment = "Synced from Shopify order ${orderDetails.orderName}",
            email = orderDetails.customerEmail,
            sendEmail = false
        )
    }

    private fun calculateShippingAmount(orderDetails: ShopifyOrderDetails): BigDecimal? {
        val orderTotal = orderDetails.totalPrice?.amount ?: return null
        val lineSum = orderDetails.lineItems.mapNotNull { it.totalPrice?.amount }.fold(BigDecimal.ZERO, BigDecimal::add)
        val shipping = orderTotal.subtract(lineSum)
        return if (shipping.abs() < BigDecimal("0.01")) null else shipping
    }

    private fun toIsoCountryCode(country: String?): String? {
        if (country.isNullOrBlank()) return null
        val normalized = country.normalize()
        Locale.getISOCountries().forEach { code ->
            val locale = Locale.Builder().setRegion(code).build()
            val candidates = listOf(locale.displayCountry, locale.getDisplayCountry(Locale.ENGLISH))
            if (candidates.any { it.normalize() == normalized }) return code
        }
        return null
    }

    private object CountryNormalizer {
        fun String.normalize(): String {
            return Normalizer.normalize(this, Normalizer.Form.NFKD)
                .replace("\u00A0", " ")
                .replace("[^A-Za-z ]".toRegex(), "")
                .trim()
                .lowercase(Locale.ENGLISH)
        }
    }

    private fun ReaiOrderSyncRecord.toStatus(existing: Boolean): ReaiSyncStatus {
        return ReaiSyncStatus(
            reaiOrderId = reaiOrderId,
            reaiInvoiceId = reaiInvoiceId,
            reaiInvoiceNumber = reaiInvoiceNumber,
            syncedAt = syncedAt,
            alreadySynced = existing
        )
    }
}
