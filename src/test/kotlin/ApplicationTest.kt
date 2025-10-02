package com.example

import com.example.database.DatabaseFactory
import com.example.models.TokenConfig
import com.example.plugins.configureAuthentication
import com.example.plugins.configureSerialization
import com.example.routes.authRoutes
import com.example.routes.userRoutes
import com.example.routes.productRoutes
import com.example.routes.orderRoutes
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.*

open class ApplicationTest {
    protected val tokenConfig = TokenConfig(
        issuer = "ktor-server",
        audience = "ktor-client",
        expiresIn = 3600000L,
        secret = "your-secret-key-here-change-in-production",
        refreshExpiresIn = 604800000L
    )

    protected fun TestApplicationBuilder.setupApplication() {
        environment {
            config = MapApplicationConfig()
        }

        application {
            DatabaseFactory.init()

            transaction {
                SchemaUtils.drop(
                    com.example.database.Users,
                    com.example.database.Products,
                    com.example.database.Orders,
                    com.example.database.OrderItems,
                    com.example.database.RefreshTokens
                )
                SchemaUtils.create(
                    com.example.database.Users,
                    com.example.database.Products,
                    com.example.database.Orders,
                    com.example.database.OrderItems,
                    com.example.database.RefreshTokens
                )

                // Create test users
                com.example.database.User.new {
                    username = "admin"
                    password = "admin123"
                    role = com.example.models.UserRole.ADMIN
                }

                com.example.database.User.new {
                    username = "testuser"
                    password = "test123"
                    role = com.example.models.UserRole.USER
                }

                // Create test products
                com.example.database.Product.new {
                    name = "Test Product 1"
                    description = "Test Description 1"
                    price = 99.99
                    stock = 10
                }

                com.example.database.Product.new {
                    name = "Test Product 2"
                    description = "Test Description 2"
                    price = 49.99
                    stock = 5
                }
            }

            configureSerialization()
            configureAuthentication()

            routing {
                authRoutes(tokenConfig)
                userRoutes()
                productRoutes()
                orderRoutes()
            }
        }
    }
}