package com.example.routes

import com.example.database.*
import com.example.models.OrderRequest
import com.example.models.OrderResponse
import com.example.models.OrderListResponse
import com.example.models.OrderItemResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

@kotlinx.serialization.Serializable
data class OrderCreateResponse(
    val orderId: Int,
    val total: Double,
    val message: String
)

fun Route.orderRoutes() {

    authenticate("auth-jwt") {
        // Создание заказа
        post("/orders") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", Int::class) ?: run {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid user"))
                    return@post
                }

                val request = call.receive<OrderRequest>()
                println("Creating order for user $userId with items: ${request.items}")

                if (request.items.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Order must contain at least one item"))
                    return@post
                }

                val result = transaction {
                    try {
                        // Check product availability and calculate total
                        var total = 0.0
                        val orderItems = mutableListOf<Pair<Product, Int>>()

                        for (item in request.items) {
                            val product = Product.findById(item.productId)
                            if (product == null) {
                                return@transaction Pair(false, "Product not found: ${item.productId}")
                            }
                            if (product.stock < item.quantity) {
                                return@transaction Pair(false, "Insufficient stock for product: ${product.name}. Available: ${product.stock}, requested: ${item.quantity}")
                            }

                            total += product.price * item.quantity
                            orderItems.add(Pair(product, item.quantity))
                        }

                        // Get user entity
                        val user = User.findById(userId)
                        if (user == null) {
                            return@transaction Pair(false, "User not found")
                        }

                        println("Creating order with total: $total")

                        // Create order
                        val order = Order.new {
                            this.userId = user.id
                            this.total = total
                        }

                        println("Order created with ID: ${order.id.value}")

                        // Create order items and update stock
                        for ((product, quantity) in orderItems) {
                            OrderItem.new {
                                this.orderId = order.id
                                this.productId = product.id
                                this.quantity = quantity
                                this.price = product.price
                            }

                            product.stock -= quantity
                            println("Updated stock for product ${product.name}: ${product.stock}")
                        }

                        Pair(true, order)
                    } catch (e: Exception) {
                        println("Error in transaction: ${e.message}")
                        e.printStackTrace()
                        Pair(false, "Database error: ${e.message}")
                    }
                }

                if (result.first) {
                    val order = result.second as Order
                    val response = OrderCreateResponse(
                        orderId = order.id.value,
                        total = order.total,
                        message = "Order created successfully"
                    )
                    call.respond(HttpStatusCode.Created, response)
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.second))
                }

            } catch (e: Exception) {
                println("Error in order creation: ${e.message}")
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error: ${e.message}"))
            }
        }

        // Получение всех заказов пользователя
        get("/orders") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", Int::class)
                val userRole = principal?.getClaim("role", String::class)

                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 10

                println("Getting orders for user: $userId, role: $userRole, page: $page, pageSize: $pageSize")

                val (orders, totalCount) = transaction {
                    try {
                        // Build query
                        val query = if (userRole != "ADMIN" && userId != null) {
                            Order.find { Orders.userId eq userId }
                        } else {
                            Order.all()
                        }

                        // Apply pagination and get results
                        val ordersList = query
                            .orderBy(Orders.id to SortOrder.DESC)
                            .limit(pageSize, ((page - 1) * pageSize).toLong())
                            .map { order ->
                                val items = OrderItem.find { OrderItems.orderId eq order.id }.map { item ->
                                    val product = Product.findById(item.productId.value)
                                    if (product == null) {
                                        OrderItemResponse(0, "Unknown Product", item.quantity, item.price)
                                    } else {
                                        OrderItemResponse(
                                            product.id.value,
                                            product.name,
                                            item.quantity,
                                            item.price
                                        )
                                    }
                                }

                                OrderResponse(
                                    order.id.value,
                                    order.userId.value,
                                    order.total,
                                    order.status,
                                    Instant.ofEpochSecond(order.createdAt).toString(),
                                    items
                                )
                            }

                        // Get total count
                        val total = if (userRole != "ADMIN" && userId != null) {
                            Order.find { Orders.userId eq userId }.count()
                        } else {
                            Order.all().count()
                        }

                        Pair(ordersList, total)
                    } catch (e: Exception) {
                        println("Error in orders query: ${e.message}")
                        throw e
                    }
                }

                call.respond(OrderListResponse(orders, totalCount.toInt(), page, pageSize))

            } catch (e: Exception) {
                println("Error getting orders: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        // Получение конкретного заказа по ID
        get("/orders/{id}") {
            try {
                val orderId = call.parameters["id"]?.toIntOrNull()
                if (orderId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid order ID"))
                    return@get
                }

                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", Int::class)
                val userRole = principal?.getClaim("role", String::class)

                println("Getting order $orderId for user: $userId, role: $userRole")

                val order = transaction {
                    try {
                        Order.findById(orderId)
                    } catch (e: Exception) {
                        println("Error finding order: ${e.message}")
                        null
                    }
                }

                if (order == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Order not found"))
                    return@get
                }

                // Проверка прав доступа
                if (userRole != "ADMIN" && order.userId.value != userId) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                    return@get
                }

                val orderResponse = transaction {
                    try {
                        val items = OrderItem.find { OrderItems.orderId eq order.id }.map { item ->
                            val product = Product.findById(item.productId.value)
                            if (product == null) {
                                OrderItemResponse(0, "Unknown Product", item.quantity, item.price)
                            } else {
                                OrderItemResponse(
                                    product.id.value,
                                    product.name,
                                    item.quantity,
                                    item.price
                                )
                            }
                        }

                        OrderResponse(
                            order.id.value,
                            order.userId.value,
                            order.total,
                            order.status,
                            Instant.ofEpochSecond(order.createdAt).toString(),
                            items
                        )
                    } catch (e: Exception) {
                        println("Error building order response: ${e.message}")
                        throw e
                    }
                }

                call.respond(orderResponse)

            } catch (e: Exception) {
                println("Error getting order: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
    }
}