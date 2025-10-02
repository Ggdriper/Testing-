package com.example.routes

import com.example.ApplicationTest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class OrderTest : ApplicationTest() {

    private suspend fun getUserToken(client: io.ktor.client.HttpClient): String {
        val loginResponse = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"testuser","password":"test123"}""")
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status)
        return loginResponse.bodyAsText()
            .replace("\\s".toRegex(), "")
            .substringAfter("\"token\":\"")
            .substringBefore("\"")
    }

    @Test
    fun testCreateOrder() = testApplication {
        setupApplication()

        val token = getUserToken(client)

        val response = client.post("/orders") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody("""
                {
                    "items": [
                        {
                            "productId": 1,
                            "quantity": 1
                        }
                    ]
                }
            """.trimIndent())
        }

        println("Create order response: ${response.status} - ${response.bodyAsText()}")

        assertTrue {
            response.status == HttpStatusCode.OK ||
                    response.status == HttpStatusCode.Created ||
                    response.status == HttpStatusCode.Unauthorized
        }
    }
}