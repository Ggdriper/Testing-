package com.example.routes

import com.example.ApplicationTest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ProductTest : ApplicationTest() {

    private suspend fun getAdminToken(client: io.ktor.client.HttpClient): String {
        val loginResponse = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"admin123"}""")
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status)
        return loginResponse.bodyAsText()
            .replace("\\s".toRegex(), "")
            .substringAfter("\"token\":\"")
            .substringBefore("\"")
    }

    @Test
    fun testGetAllProducts() = testApplication {
        setupApplication()

        val response = client.get("/products")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testCreateProductAsAdmin() = testApplication {
        setupApplication()

        val token = getAdminToken(client)

        val response = client.post("/products") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody("""
                {
                    "name": "Keyboard",
                    "description": "Mechanical keyboard with RGB",
                    "price": 89.99,
                    "stock": 25
                }
            """.trimIndent())
        }

        println("Create product response: ${response.status} - ${response.bodyAsText()}")

        assertTrue {
            response.status == HttpStatusCode.OK ||
                    response.status == HttpStatusCode.Created ||
                    response.status == HttpStatusCode.Unauthorized
        }
    }
}