package com.respiroc.shopifyreaisync.service

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Service

@Service
class CookieService {

    fun setCookie(request: HttpServletRequest, response: HttpServletResponse, name: String, value: String, maxAge: Int, httpOnly: Boolean, sameSite: String) {
        val secure = request.isSecure
        val cookieString = StringBuilder()
        cookieString.append("${name}=${value}")
        cookieString.append("; Max-Age=${maxAge}")
        cookieString.append("; Path=/")
        if (httpOnly) cookieString.append("; HttpOnly")
        if (secure) cookieString.append("; Secure")
        
        val sameSiteValue = when (sameSite.lowercase()) {
            "lax" -> "Lax"
            "strict" -> "Strict"
            "none" -> "None"
            else -> "Lax"
        }
        cookieString.append("; SameSite=${sameSiteValue}")

        response.addHeader("Set-Cookie", cookieString.toString())
    }

    fun getCookie(request: HttpServletRequest, name: String): String? {
        return request.cookies?.firstOrNull { it.name == name }?.value
    }

    fun clearCookie(response: HttpServletResponse, name: String) {
        val cookieString = StringBuilder()
        cookieString.append("${name}=")
        cookieString.append("; Max-Age=0")
        cookieString.append("; Path=/")
        cookieString.append("; HttpOnly")
        cookieString.append("; Secure")
        cookieString.append("; SameSite=Lax")

        response.addHeader("Set-Cookie", cookieString.toString())
    }
}
