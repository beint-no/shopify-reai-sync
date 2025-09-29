package com.respiroc.shopifyreaisync.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

@Component
class ReaiApiClient(
    private val restClientBuilder: RestClient.Builder,
    @Value("\${reai.api-base-url}")
    private val reaiApiBaseUrl: String
) {

    fun searchCustomers(token: String, name: String): List<ReaiCustomerResponse> {
        val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8)
        val endpoint = apiEndpoint(reaiApiBaseUrl, "/customers?name=$encodedName")
        val restClient = restClientBuilder.build()
        return restClient.get()
            .uri(endpoint)
            .header("Authorization", "Bearer $token")
            .retrieve()
            .body(object : ParameterizedTypeReference<List<ReaiCustomerResponse>>() {})
            ?: emptyList()
    }

    fun createCustomer(token: String, request: ReaiCreateCustomerRequest): ReaiCustomerResponse {
        val restClient = restClientBuilder.build()
        return restClient.post()
            .uri(apiEndpoint(reaiApiBaseUrl, "/customers"))
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(ReaiCustomerResponse::class.java)
            ?: throw IllegalStateException("ReAI customer response was empty")
    }

    fun createOrder(token: String, request: ReaiNewOrderRequest): ReaiOrderResponse {
        val restClient = restClientBuilder.build()
        return restClient.post()
            .uri(apiEndpoint(reaiApiBaseUrl, "/order"))
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(ReaiOrderResponse::class.java)
            ?: throw IllegalStateException("ReAI order response was empty")
    }

    fun createInvoice(token: String, request: ReaiNewInvoiceRequest): ReaiInvoiceResponse {
        val restClient = restClientBuilder.build()
        return restClient.post()
            .uri(apiEndpoint(reaiApiBaseUrl, "/invoice"))
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(ReaiInvoiceResponse::class.java)
            ?: throw IllegalStateException("ReAI invoice response was empty")
    }

    private fun apiEndpoint(baseUrl: String, path: String): String {
        val sanitizedBase = baseUrl.trimEnd('/')
        val sanitizedPath = if (path.startsWith('/')) path else "/$path"
        return "$sanitizedBase$sanitizedPath"
    }
}

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
    val unitPrice: java.math.BigDecimal,
    val discount: java.math.BigDecimal?,
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
