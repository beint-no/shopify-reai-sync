package com.respiroc.shopifyreaisync.dto.store

data class DashboardQueryParameters(
    val shop: String? = null,
    val installed: String? = null,
    val error: String? = null,
    val accessToken: String? = null,
    val reaiConnected: String? = null,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val scope: String? = null
)
