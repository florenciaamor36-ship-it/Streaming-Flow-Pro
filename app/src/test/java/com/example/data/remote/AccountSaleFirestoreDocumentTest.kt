package com.example.data.remote

import com.example.data.local.ProfileSaleEntity
import com.example.data.local.StreamingAccountEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AccountSaleFirestoreDocumentTest {
    private fun utcMillis(day: String) = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .parse(day)!!.time

    @Test
    fun `account mapper round trips safe web fields and excludes passwords and supplier phone`() {
        val account = StreamingAccountEntity(
            id = "fixture-account", userId = "alice-test-uid", platformName = "Netflix",
            accountEmail = "fixture@example.invalid", accountPassword = "not-for-sync",
            maxProfiles = 4, cost = 18.5, purchaseDate = utcMillis("2026-11-19"),
            expirationDate = utcMillis("2026-12-19"), supplierName = "Fixture Provider",
            supplierPhone = "not-for-sync", notes = "synthetic"
        )
        val shared = account.toSharedFirestoreDocument()
        assertEquals("Netflix", shared["platform"])
        assertEquals("2026-11-19", shared["purchaseDate"])
        assertEquals("2026-12-19", shared["renewalDate"])
        assertEquals(4, shared["totalProfiles"])
        assertEquals("Fixture Provider", shared["providerName"])
        assertFalse(shared.keys.any { it.contains("password", true) || it.contains("email", true) || it.contains("phone", true) || it.contains("pin", true) })

        val restored = sharedDocumentToAccount("fixture-account", shared)
        assertEquals(account.id, restored.id)
        assertEquals(account.userId, restored.userId)
        assertEquals(account.platformName, restored.platformName)
        assertEquals(account.maxProfiles, restored.maxProfiles)
        assertEquals(account.cost, restored.cost, 0.0)
        assertEquals(account.purchaseDate, restored.purchaseDate)
        assertEquals(account.expirationDate, restored.expirationDate)
        assertEquals("", restored.accountEmail)
        assertEquals("", restored.accountPassword)
        assertEquals("", restored.supplierPhone)
    }

    @Test
    fun `sale mapper round trips business fields and excludes login and profile PIN`() {
        val sale = ProfileSaleEntity(
            id = "fixture-sale", userId = "alice-test-uid", accountId = "fixture-account",
            platformName = "Netflix", accountEmail = "fixture@example.invalid", accountPassword = "not-for-sync",
            profileName = "Screen A", profilePin = "not-for-sync", clientName = "Fixture Customer",
            clientPhone = "0000000000", salePrice = 20.0, costPrice = 5.0,
            saleDate = utcMillis("2026-11-19"), expirationDate = utcMillis("2026-12-19"),
            paymentStatus = "PAID", renewalStatus = "ACTIVE", saleType = "PROFILE", notes = "synthetic"
        )
        val shared = sale.toSharedFirestoreDocument()
        assertEquals("profile", shared["type"])
        assertEquals("2026-11-19", shared["startDate"])
        assertEquals("2026-12-19", shared["endDate"])
        assertEquals("paid", shared["paymentStatus"])
        assertEquals(15.0, shared["profit"])
        assertFalse(shared.keys.any { it.contains("password", true) || it.contains("email", true) || it.contains("pin", true) })

        val restored = sharedDocumentToSale("fixture-sale", shared)
        assertEquals(sale.id, restored.id)
        assertEquals(sale.userId, restored.userId)
        assertEquals(sale.accountId, restored.accountId)
        assertEquals(sale.clientName, restored.clientName)
        assertEquals(sale.clientPhone, restored.clientPhone)
        assertEquals(sale.salePrice, restored.salePrice, 0.0)
        assertEquals(sale.costPrice, restored.costPrice, 0.0)
        assertEquals(sale.saleDate, restored.saleDate)
        assertEquals(sale.expirationDate, restored.expirationDate)
        assertEquals("", restored.accountEmail)
        assertEquals("", restored.accountPassword)
        assertEquals("", restored.profilePin)
    }
}
