package com.respiroc.shopifyreaisync.service

import com.respiroc.shopifyreaisync.dto.reai.ReaiCreateCustomerRequest
import com.respiroc.shopifyreaisync.dto.reai.ReaiCustomerResponse
import com.respiroc.shopifyreaisync.dto.reai.ReaiInvoiceResponse
import com.respiroc.shopifyreaisync.dto.reai.ReaiNewInvoiceRequest
import com.respiroc.shopifyreaisync.dto.reai.ReaiNewOrderRequest
import com.respiroc.shopifyreaisync.dto.reai.ReaiOrderResponse
import com.respiroc.shopifyreaisync.dto.reai.ReaiProductSnapshotResponse
import com.respiroc.shopifyreaisync.dto.reai.ReaiProductSyncRequest
import com.respiroc.shopifyreaisync.dto.reai.ReaiProductSyncResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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

    fun fetchProduct(token: String, productId: Long): ReaiProductSnapshotResponse? {
        val restClient = restClientBuilder.build()
        return try {
            restClient.get()
                .uri(apiEndpoint(reaiApiBaseUrl, "/product/$productId"))
                .header("Authorization", "Bearer $token")
                .retrieve()
                .body(ReaiProductSnapshotResponse::class.java)
        } catch (notFound: HttpClientErrorException.NotFound) {
            null
        }
    }

    fun syncProduct(token: String, request: ReaiProductSyncRequest): ReaiProductSyncResponse {
        val restClient = restClientBuilder.build()
        return restClient.post()
            .uri(apiEndpoint(reaiApiBaseUrl, "/product/create"))
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(ReaiProductSyncResponse::class.java)
            ?: throw IllegalStateException("ReAI product sync response was empty")
    }

    private fun apiEndpoint(baseUrl: String, path: String): String {
        val sanitizedBase = baseUrl.trimEnd('/')
        val sanitizedPath = if (path.startsWith('/')) path else "/$path"
        return "$sanitizedBase$sanitizedPath"
    }
}
