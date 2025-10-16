package com.respiroc.shopifyreaisync.dto.shared

import java.math.BigDecimal

data class MoneyAmount(
    val amount: BigDecimal,
    val currencyCode: String
)
