package com.example

import com.example.database.DatabaseFactory
import com.example.database.OrderItems
import com.example.database.Orders
import com.example.database.Products
import com.example.database.RefreshTokens
import com.example.database.Users
import com.example.models.TokenConfig
import com.example.plugins.configureAuthentication
import com.example.plugins.configureSerialization
import com.example.plugins.configureSwagger
import com.example.routes.authRoutes
import com.example.routes.userRoutes
import com.example.routes.productRoutes
import com.example.routes.orderRoutes
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.h2.tools.Server
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.DriverManager

fun main() {
    // Запускаем H2 Console на порту 8082
    val h2Console = Server.createWebServer("-web", "-webAllowOthers", "-webPort", "8082")
    h2Console.start()
    println("H2 Console available at: http://localhost:8082")

    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

// Остальной код без изменений...
fun Application.module() {
    // Initialize database
    DatabaseFactory.init()

    // Create tables and seed data
    transaction {

        SchemaUtils.drop(
            Users, Products, Orders, OrderItems, RefreshTokens
        )
        SchemaUtils.create(
            com.example.database.Users,
            com.example.database.Products,
            com.example.database.Orders,
            com.example.database.OrderItems,
            com.example.database.RefreshTokens
        )

        // Создаем пользователей
        if (com.example.database.User.find { com.example.database.Users.username eq "admin" }.empty()) {
            com.example.database.User.new {
                username = "admin"
                password = "admin123"
                role = com.example.models.UserRole.ADMIN
            }
        }

        if (com.example.database.User.find { com.example.database.Users.username eq "user" }.empty()) {
            com.example.database.User.new {
                username = "user"
                password = "user123"
                role = com.example.models.UserRole.USER
            }
        }

        // Create sample products
        if (com.example.database.Product.count() == 0L) {
            com.example.database.Product.new {
                name = "Laptop"
                description = "High-performance laptop"
                price = 999.99
                stock = 10
            }
            com.example.database.Product.new {
                name = "Mouse"
                description = "Wireless mouse"
                price = 29.99
                stock = 50
            }
        }
    }

    val tokenConfig = TokenConfig(
        issuer = "ktor-server",
        audience = "ktor-client",
        expiresIn = 3600L * 1000L,
        secret = "your-secret-key-here-change-in-production",
        refreshExpiresIn = 7L * 24L * 3600L * 1000L
    )

    configureSerialization()
    configureAuthentication()
    configureSwagger()

    routing {
        authRoutes(tokenConfig)
        userRoutes()
        productRoutes()
        orderRoutes()

        // Добавляем debug endpoint для просмотра данных
        get("/debug/database") {
            val result = transaction {
                val users = com.example.database.User.all().toList()
                val products = com.example.database.Product.all().toList()
                val orders = com.example.database.Order.all().toList()
                val orderItems = com.example.database.OrderItem.all().toList()
                val refreshTokens = com.example.database.RefreshToken.all().toList()

                mapOf(
                    "users" to users.map {
                        mapOf(
                            "id" to it.id.value,
                            "username" to it.username,
                            "role" to it.role.name,
                            "createdAt" to it.createdAt
                        )
                    },
                    "products" to products.map {
                        mapOf(
                            "id" to it.id.value,
                            "name" to it.name,
                            "description" to it.description,
                            "price" to it.price,
                            "stock" to it.stock,
                            "createdAt" to it.createdAt
                        )
                    },
                    "orders" to orders.map {
                        mapOf(
                            "id" to it.id.value,
                            "userId" to it.userId.value,
                            "total" to it.total,
                            "status" to it.status.name,
                            "createdAt" to it.createdAt
                        )
                    },
                    "orderItems" to orderItems.map {
                        mapOf(
                            "id" to it.id.value,
                            "orderId" to it.orderId.value,
                            "productId" to it.productId.value,
                            "quantity" to it.quantity,
                            "price" to it.price
                        )
                    },
                    "refreshTokens" to refreshTokens.map {
                        mapOf(
                            "id" to it.id.value,
                            "userId" to it.userId.value,
                            "token" to it.token.substring(0, 10) + "...",
                            "expiresAt" to it.expiresAt,
                            "createdAt" to it.createdAt
                        )
                    }
                )
            }
            call.respond(result)
        }
    }
}