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

@Controller
@RequestMapping("/oauth")
class ShopifyOAuthController(
    private val shopifyOAuthService: ShopifyOAuthService
) {
    @GetMapping("/install")
    fun install(@RequestParam("shop") shopDomain: String): String {
        val redirectUri = shopifyOAuthService.buildInstallationRedirect(shopDomain)
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
}