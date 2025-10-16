package com.respiroc.shopifyreaisync.controller

import com.respiroc.shopifyreaisync.config.RequestContext
import com.respiroc.shopifyreaisync.model.ReaiConnection
import com.respiroc.shopifyreaisync.dto.connection.ReaiConnectionStatus
import com.respiroc.shopifyreaisync.dto.order.OrderSearchRequest
import com.respiroc.shopifyreaisync.dto.order.OrderSyncRequest
import com.respiroc.shopifyreaisync.dto.order.ReaiSyncResult
import com.respiroc.shopifyreaisync.dto.store.DashboardQueryParameters
import com.respiroc.shopifyreaisync.dto.store.ShopifyDisconnectRequest
import com.respiroc.shopifyreaisync.service.CookieService
import com.respiroc.shopifyreaisync.service.OrderNotFoundException
import com.respiroc.shopifyreaisync.service.ReaiConnectionService
import com.respiroc.shopifyreaisync.service.ReaiOrderSyncService
import com.respiroc.shopifyreaisync.service.ReaiTokenClient
import com.respiroc.shopifyreaisync.service.ShopifyOrderService
import com.respiroc.shopifyreaisync.service.ShopifyInstallationService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
class OrderSearchController(
    private val shopifyInstallationService: ShopifyInstallationService,
    private val shopifyOrderService: ShopifyOrderService,
    private val reaiConnectionService: ReaiConnectionService,
    private val reaiOrderSyncService: ReaiOrderSyncService,
    private val cookieService: CookieService,
    private val reaiTokenClient: ReaiTokenClient
) {
    @GetMapping("/")
    fun home(
        @RequestParam(value = "shop", required = false) shopDomain: String?,
        @RequestParam(value = "installed", required = false) installed: String?,
        @RequestParam(value = "error", required = false) errorMessage: String?,
        @RequestParam(value = "access_token", required = false) accessTokenParam: String?,
        @RequestParam(value = "reaiConnected", required = false) reaiConnectedFlag: String?,
        @RequestParam(value = "client_id", required = false) clientIdParam: String?,
        @RequestParam(value = "client_secret", required = false) clientSecretParam: String?,
        @RequestParam(value = "scope", required = false) scopeParam: String?,
        model: Model,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): String {
        val queryParameters = DashboardQueryParameters(
            shop = shopDomain,
            installed = installed,
            error = errorMessage,
            accessToken = accessTokenParam,
            reaiConnected = reaiConnectedFlag,
            clientId = clientIdParam,
            clientSecret = clientSecretParam,
            scope = scopeParam
        )
        populateDashboard(queryParameters, model, request, response)
        model.addAttribute("activeSection", "orders")
        model.addAttribute("orderNumber", "")
        return "order"
    }

    @GetMapping("/products")
    fun productsPage(
        @RequestParam(value = "shop", required = false) shopDomain: String?,
        @RequestParam(value = "installed", required = false) installed: String?,
        @RequestParam(value = "error", required = false) errorMessage: String?,
        @RequestParam(value = "access_token", required = false) accessTokenParam: String?,
        @RequestParam(value = "reaiConnected", required = false) reaiConnectedFlag: String?,
        @RequestParam(value = "client_id", required = false) clientIdParam: String?,
        @RequestParam(value = "client_secret", required = false) clientSecretParam: String?,
        @RequestParam(value = "scope", required = false) scopeParam: String?,
        model: Model,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): String {
        val queryParameters = DashboardQueryParameters(
            shop = shopDomain,
            installed = installed,
            error = errorMessage,
            accessToken = accessTokenParam,
            reaiConnected = reaiConnectedFlag,
            clientId = clientIdParam,
            clientSecret = clientSecretParam,
            scope = scopeParam
        )
        populateDashboard(queryParameters, model, request, response)
        model.addAttribute("activeSection", "products")
        model.addAttribute("productSearchTerm", "")
        return "product"
    }

    @GetMapping("/stores")
    fun storesPage(
        @RequestParam(value = "shop", required = false) shopDomain: String?,
        @RequestParam(value = "installed", required = false) installed: String?,
        @RequestParam(value = "error", required = false) errorMessage: String?,
        @RequestParam(value = "access_token", required = false) accessTokenParam: String?,
        @RequestParam(value = "reaiConnected", required = false) reaiConnectedFlag: String?,
        @RequestParam(value = "client_id", required = false) clientIdParam: String?,
        @RequestParam(value = "client_secret", required = false) clientSecretParam: String?,
        @RequestParam(value = "scope", required = false) scopeParam: String?,
        model: Model,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): String {
        val queryParameters = DashboardQueryParameters(
            shop = shopDomain,
            installed = installed,
            error = errorMessage,
            accessToken = accessTokenParam,
            reaiConnected = reaiConnectedFlag,
            clientId = clientIdParam,
            clientSecret = clientSecretParam,
            scope = scopeParam
        )
        populateDashboard(queryParameters, model, request, response)
        model.addAttribute("activeSection", "stores")
        return "store"
    }

    @PostMapping("/orders/search")
    fun searchOrder(
        @RequestParam("shop") shopDomain: String,
        @RequestParam("orderNumber") orderNumber: String,
        @RequestParam("tenant_id") tenantId: Long,
        model: Model
    ): String {
        RequestContext.setTenantId(tenantId)
        val searchRequest = OrderSearchRequest(
            shopDomain = shopDomain,
            orderNumber = orderNumber,
            tenantId = tenantId
        )
        return try {
            val orderDetails = shopifyOrderService.fetchOrderByNumber(searchRequest)
            model.addAttribute("orderDetails", orderDetails)
            model.addAttribute("orderNumber", orderNumber)
            model.addAttribute("selectedShop", shopDomain)
            model.addAttribute("tenantId", tenantId)
            val connection = reaiConnectionService.findByShopDomainAndTenantId(shopDomain, tenantId)
                ?: throw IllegalArgumentException("ReAI connection not found for shop: $shopDomain and tenant: $tenantId")
            model.addAttribute("reaiConnectionConfigured", reaiConnectionService.hasValidToken(connection))
            model.addAttribute("reaiSyncStatus", reaiOrderSyncService.findSyncRecord(searchRequest))
            "orders/search-result :: orderResult"
        } catch (exception: OrderNotFoundException) {
            model.addAttribute("orderNumber", orderNumber)
            model.addAttribute("selectedShop", shopDomain)
            model.addAttribute("tenantId", tenantId)
            model.addAttribute("orderNotFound", true)
            "orders/search-result :: orderResult"
        }
    }

    @PostMapping("/orders/sync-reai")
    fun syncOrder(
        @RequestParam("shop") shopDomain: String,
        @RequestParam("orderNumber") orderNumber: String,
        @RequestParam("tenantId") tenantId: Long,
        model: Model
    ): String {
        val syncRequest = OrderSyncRequest(
            shopDomain = shopDomain,
            orderNumber = orderNumber,
            tenantId = tenantId
        )
        val result = reaiOrderSyncService.syncOrder(syncRequest)
        model.addAttribute("selectedShop", shopDomain)
        enrichModelWithSyncResult(model, result)
        return "orders/search-result :: orderResult"
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleValidationErrors(exception: IllegalArgumentException, model: Model): String {
        model.addAttribute("validationError", exception.message)
        model.addAttribute("orderNumber", "")
        return "orders/search-result :: orderResult"
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIntegrationErrors(exception: IllegalStateException, model: Model): String {
        model.addAttribute("technicalError", exception.message)
        model.addAttribute("orderNumber", "")
        model.addAttribute("reaiConnectionConfigured", false)
        return "orders/search-result :: orderResult"
    }

    private fun populateDashboard(
        queryParameters: DashboardQueryParameters,
        model: Model,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        val normalizedShop = queryParameters.shop?.lowercase()
        var reaiConnection: ReaiConnection? = null
        var reaiConnected = false
        var currentTenantId: Long? = null
        val sanitizedClientId = queryParameters.clientId?.trim()?.takeIf { it.isNotEmpty() }
        val sanitizedClientSecret = queryParameters.clientSecret?.trim()?.takeIf { it.isNotEmpty() }
        val requestedScopes = queryParameters.scope
            ?.split(',', ' ')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        val normalizedScope = requestedScopes.takeIf { it.isNotEmpty() }?.joinToString(" ")
        val hasClientCredentials = sanitizedClientId != null && sanitizedClientSecret != null
        val credentialClientId = if (hasClientCredentials) sanitizedClientId else null
        val credentialClientSecret = if (hasClientCredentials) sanitizedClientSecret else null
        val credentialScope = if (hasClientCredentials) normalizedScope else null
        val incomingAccessToken = when {
            !queryParameters.accessToken.isNullOrBlank() -> queryParameters.accessToken
            hasClientCredentials -> {
                try {
                    reaiTokenClient.exchangeClientCredentials(
                        credentialClientId!!,
                        credentialClientSecret!!,
                        requestedScopes
                    )
                } catch (ignored: Exception) {
                    null
                }
            }
            else -> null
        }

        if (!incomingAccessToken.isNullOrBlank()) {
            try {
                reaiConnection = reaiConnectionService.storeAccessToken(
                    incomingAccessToken,
                    normalizedShop,
                    credentialClientId,
                    credentialClientSecret,
                    credentialScope
                )
                currentTenantId = reaiConnection.tenantId
                reaiConnected = reaiConnectionService.hasValidToken(reaiConnection)
                if (reaiConnected) {
                    val activeToken = reaiConnection.accessToken
                    if (!activeToken.isNullOrBlank()) {
                        cookieService.setCookie(request, response, "reai_access_token", activeToken, 3600, true, "Lax")
                    }
                    RequestContext.setTenantId(currentTenantId)
                }
            } catch (ignored: Exception) {
            }
        } else {
            val cookieAccessToken = cookieService.getCookie(request, "reai_access_token")
            if (!cookieAccessToken.isNullOrBlank()) {
                try {
                    reaiConnection = reaiConnectionService.storeAccessToken(cookieAccessToken, null)
                    currentTenantId = reaiConnection.tenantId
                    RequestContext.setTenantId(currentTenantId)
                    reaiConnected = reaiConnectionService.hasValidToken(reaiConnection)
                    if (reaiConnected) {
                        val activeToken = reaiConnection.accessToken
                        if (!activeToken.isNullOrBlank()) {
                            cookieService.setCookie(request, response, "reai_access_token", activeToken, 3600, true, "Lax")
                        }
                    }
                } catch (ignored: Exception) {
                    cookieService.clearCookie(response, "reai_access_token")
                }
            }
        }

        model.addAttribute("reaiConnected", reaiConnected)
        model.addAttribute("currentTenantId", currentTenantId)
        model.addAttribute("errorMessage", queryParameters.error)
        model.addAttribute("reaiRecentlyConnected", queryParameters.reaiConnected == "1")

        val installationsForTenant = if (reaiConnected && currentTenantId != null) {
            shopifyInstallationService.findByReaiConnectionTenantId(currentTenantId)
        } else {
            emptyList()
        }
        model.addAttribute("installationsForTenant", installationsForTenant)

        val effectiveSelectedShop = normalizedShop ?: installationsForTenant.firstOrNull()?.shopDomain

        val shopifyStoreLinked = reaiConnected && installationsForTenant.isNotEmpty()
        model.addAttribute("shopifyStoreLinked", shopifyStoreLinked)
        model.addAttribute("promptInstallShopify", reaiConnected)

        val allInstallations = if (reaiConnected) shopifyInstallationService.findAll() else emptyList()
        val reaiStatuses = allInstallations.associate { installation ->
            val connection = reaiConnectionService.findByShopifyInstallation(installation)
            installation.shopDomain to ReaiConnectionStatus(
                connected = reaiConnectionService.hasValidToken(connection),
                expiresAt = connection?.accessTokenExpiresAt,
                tenantId = connection?.tenantId,
                autoSync = connection?.autoSync ?: false
            )
        }
        model.addAttribute("installations", allInstallations)
        model.addAttribute("reaiStatuses", reaiStatuses)

        model.addAttribute("selectedShop", effectiveSelectedShop)
        model.addAttribute("installedShop", if (queryParameters.installed == "1") normalizedShop else null)

    }

    private fun enrichModelWithSyncResult(model: Model, result: ReaiSyncResult) {
        model.addAttribute("orderDetails", result.orderDetails)
        model.addAttribute("orderNumber", result.orderDetails.orderNumber)
        model.addAttribute("reaiConnectionConfigured", true)
        model.addAttribute("reaiSyncStatus", result.status)
    }

    @PostMapping("/shopify/disconnect")
    fun disconnectShopify(
        @RequestParam("tenant_id") tenantId: Long,
        @RequestParam("shop_domain") shopDomain: String,
        redirectAttributes: RedirectAttributes,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): String {
        val disconnectRequest = ShopifyDisconnectRequest(
            tenantId = tenantId,
            shopDomain = shopDomain
        )
        reaiConnectionService.disconnectShopifyInstallation(disconnectRequest)
        redirectAttributes.addAttribute("message", "Shopify store disconnected successfully!")
        RequestContext.clear()
        return "redirect:/stores"
    }

}
