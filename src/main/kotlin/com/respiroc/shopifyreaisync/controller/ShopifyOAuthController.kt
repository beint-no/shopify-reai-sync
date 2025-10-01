package com.respiroc.shopifyreaisync.controller

import com.respiroc.shopifyreaisync.oauth.ShopifyOAuthService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

@Controller
@RequestMapping("/oauth")
class ShopifyOAuthController(
    private val shopifyOAuthService: ShopifyOAuthService
) {
    @GetMapping("/install")
    fun install(
        @RequestParam("shop") shopDomain: String,
        @RequestParam(value = "tenant_id", required = false) tenantId: Long?
    ): String {
        val normalizedShopDomain = normalizeShopDomain(shopDomain)
        val redirectUri = shopifyOAuthService.buildInstallationRedirect(normalizedShopDomain, tenantId)
        return "redirect:${redirectUri}"
    }

    @GetMapping("/callback")
    fun callback(request: HttpServletRequest, redirectAttributes: RedirectAttributes): String {
        val parameters = shopifyOAuthService.extractParameters(request)
        val installation = shopifyOAuthService.handleCallback(parameters)
        redirectAttributes.addAttribute("shop", installation.shopDomain)
        redirectAttributes.addAttribute("installed", "1")
        return "redirect:/"
    }

    @ExceptionHandler(value = [IllegalArgumentException::class, IllegalStateException::class])
    fun handleCallbackErrors(exception: RuntimeException): String {
        val encodedMessage = URLEncoder.encode(exception.message ?: "OAuth error", StandardCharsets.UTF_8)
        return "redirect:/?error=${encodedMessage}"
    }

    private fun normalizeShopDomain(input: String): String {
        var cleaned = input.trim()
        require(cleaned.isNotEmpty()) { "Shop name is required" }
        cleaned = cleaned.lowercase(Locale.US)
        cleaned = cleaned.removePrefix("https://")
        cleaned = cleaned.removePrefix("http://")
        if (cleaned.startsWith("www.")) {
            cleaned = cleaned.removePrefix("www.")
        }
        val name = cleaned.removeSuffix(".myshopify.com")
        require(name.isNotEmpty()) { "Shop name is required" }
        require(shopNamePattern.matches(name)) { "Invalid shop name" }
        return "$name.myshopify.com"
    }

    companion object {
        private val shopNamePattern = Regex("^[a-z0-9][a-z0-9-]*$")
    }
}
