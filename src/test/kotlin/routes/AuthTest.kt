package com.example.routes

import com.example.ApplicationTest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class AuthTest : ApplicationTest() {

    @Test
    fun testLoginSuccess() = testApplication {
        setupApplication()

        val response = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"testuser","password":"test123"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue { body.contains("token") }
    }

    @Test
    fun testAdminLoginSuccess() = testApplication {
        setupApplication()

        val response = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"admin123"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue { body.contains("token") }
    }
}