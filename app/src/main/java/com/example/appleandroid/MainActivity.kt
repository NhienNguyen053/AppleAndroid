package com.example.appleandroid

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.appleandroid.ui.theme.AppleAndroidTheme
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import com.auth0.android.jwt.JWT
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppleAndroidTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "login") {
                    composable("login") {
                        Login(navController = navController, context = this@MainActivity)
                    }
                    composable("home") {
                        HomePage(navController = navController, context = this@MainActivity)
                    }
                }
            }
        }
    }
}

fun createTrustAllSslSocketFactory(): OkHttpClient {
    val trustAllCerts = arrayOf<TrustManager>(
        @SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    )
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, trustAllCerts, SecureRandom())
    val sslSocketFactory = sslContext.socketFactory
    val client = OkHttpClient.Builder()
        .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .build()
    return client
}

@Composable
fun Login(modifier: Modifier = Modifier, context: Context, navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var warningMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.apple_logo),
            contentDescription = "App Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(128.dp)
                .align(alignment = Alignment.CenterHorizontally)
                .padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Black,
                unfocusedBorderColor = Color.Gray
            )
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible)
                    painterResource(id = R.drawable.ic_visibility_off)
                else
                    painterResource(id = R.drawable.ic_visibility)
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(painter = image, contentDescription = "Toggle password visibility")
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Black,
                unfocusedBorderColor = Color.Gray
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )

        Button(
            onClick = {
                scope.launch {
                    warningMessage = ""
                    makeApiCall(email, password, context) { message ->
                        warningMessage = message
                        if (message.isEmpty()) {
                            navController.navigate("home")
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White
            )
        ) {
            Text(text = "Login")
        }

        if (warningMessage.isNotEmpty()) {
            Text(
                text = warningMessage,
                color = Color.Red,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun HomePage(modifier: Modifier = Modifier, context: Context, navController: NavController) {
    val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val token = sharedPref.getString("auth_token", null)
    val jwt = token?.let { JWT(it) }

    val ordersState = remember { mutableStateOf<List<Order>?>(null) }
    val loadingState = remember { mutableStateOf(true) }

    val role = jwt?.getClaim("http://schemas.microsoft.com/ws/2008/06/identity/claims/role")?.asString()
    val id = jwt?.getClaim("Id")?.asString()
    LaunchedEffect(Unit) {
        ordersState.value = fetchOrders(id, token)
        loadingState.value = false
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (loadingState.value) {
            CircularProgressIndicator()
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val firstName = jwt?.getClaim("FirstName")?.asString() ?: "Unknown"
                Text(text = "Welcome back, $firstName", fontSize = 24.sp)

                Button(
                    onClick = {
                        navController.navigate("login") {
                            popUpTo(0)
                        }
                        deleteToken(context)
                    },
                    modifier = Modifier,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                    ),
                ) {
                    Text("Logout")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ordersState.value?.let { orders ->
                if (orders.isEmpty()) {
                    Text("No orders available.")
                } else {
                    OrderList(
                        orders = orders,
                        role = role,
                        token = token,
                        onOrderUpdate = { updatedOrder ->
                            ordersState.value = ordersState.value?.map { if (it.id == updatedOrder.id) updatedOrder else it }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OrderList(orders: List<Order>, role: String?, token: String?, onOrderUpdate: (Order) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(orders) { order ->
            OrderRow(order = order, role = role, token = token, onOrderUpdate = onOrderUpdate)
        }
    }
}

@Composable
fun OrderRow(order: Order, role: String?, token: String?, onOrderUpdate: (Order) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var showDispatchDialog by remember { mutableStateOf(false) }
    var showShippingDialog by remember { mutableStateOf(false) }
    var selectedDriver by remember { mutableStateOf<Driver?>(null) }
    var desc by remember { mutableStateOf("") }
    var temp by remember { mutableStateOf("") }
    val jwt = token?.let { JWT(it) }
    val id = jwt?.getClaim("Id")?.asString()

    val backgroundColor = when {
        order.status == "Delivered" -> Color.Green
        order.status == "Shipping" -> Color.Yellow
        order.shippingDetails.any { it.dispatchedToId != null } -> Color(0xFFFFA500)
        else -> Color.LightGray
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(backgroundColor, shape = RoundedCornerShape(8.dp))
            .padding(16.dp)
            .clickable { isExpanded = !isExpanded }
    ) {
        Text(text = "Order ID: ${order.id}", fontWeight = FontWeight.Bold)
        if (isExpanded) {
            Text(text = "Total: ${order.amountTotal} ₫")
            Text(text = "Date Created: ${convertToVietnamTime(order.dateCreated)}")
            Text(text = "Product Details:")
            order.productDetails.forEach { product ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    AsyncImage(
                        model = product.image,
                        contentDescription = product.productName,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(text = "Name: ${product.productName}")
                        Text(text = "Quantity: ${product.quantity}")
                    }
                }
            }
            Text(text = "Shipping Details:")
            order.shippingDetails.forEach { detail ->
                Column {
                    Text(text = "- ${detail.note}")
                    Text(text = "  ${convertToVietnamTime(detail.dateCreated)}")
                }
            }
            Text(text = "Status: ${order.status}")
            if (role == "Dispatcher"){
                if (backgroundColor != Color.LightGray) {
                    Text(text = "Dispatched", modifier = Modifier.padding(top = 16.dp))
                } else {
                    Button(
                        onClick = { showDispatchDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(text = "Dispatch")
                    }
                }
            } else {
                when (backgroundColor) {
                    Color(0xFFFFA500) -> {
                        Button(
                            onClick = { showShippingDialog = true; status = "Shipping" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(text = "Shipping")
                        }
                    }
                    Color.Yellow -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Button(
                                onClick = { showShippingDialog = true; status = "Shipping" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Black,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(text = "Update Shipping")
                            }
                            Button(
                                onClick = { showShippingDialog = true; status = "Delivered" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Black,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(text = "Delivered")
                            }
                        }
                    }
                    else -> {
                        Text(text = "Delivered", modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
            if (showDispatchDialog) {
                DispatchDialog(
                    onDismiss = { showDispatchDialog = false },
                    onConfirm = { driver ->
                        selectedDriver = driver
                        showDispatchDialog = false
                        val newShippingDetail = ShippingDetails(
                            dispatcherId = id,
                            dispatchedToId = driver?.id,
                            pickupAddress = order.shippingDetails.firstOrNull { it.pickupAddress != null }?.pickupAddress,
                            note = desc,
                            dateCreated = getCurrentDateTime()
                        )
                        CoroutineScope(Dispatchers.Main).launch {
                            dispatchOrder(token, order.id, id, driver?.id, newShippingDetail.pickupAddress, desc, newShippingDetail.dateCreated)
                        }
                        val updatedOrder = order.copy(
                            shippingDetails = order.shippingDetails + newShippingDetail
                        )
                        onOrderUpdate(updatedOrder)
                    },
                    orderId = order.id,
                    token = token,
                    onDescriptionChange = { desc = it },
                    address = order.shippingDetails.firstOrNull { it.pickupAddress != null }?.pickupAddress,
                )
            }
            if (showShippingDialog) {
                ShippingDialog(
                    onDismiss = { showShippingDialog = false },
                    onConfirm = {
                        showShippingDialog = false
                        temp = desc
                        val newShippingDetail = ShippingDetails(
                            dispatcherId = id,
                            dispatchedToId = null,
                            pickupAddress = null,
                            note = desc,
                            dateCreated = getCurrentDateTime(),
                        )
                        CoroutineScope(Dispatchers.Main).launch {
                            shippingOrder(token, order.id, id, null, null, temp, newShippingDetail.dateCreated, status)
                        }
                        desc = ""
                        val updatedOrder = order.copy(
                            shippingDetails = order.shippingDetails + newShippingDetail,
                            status = status
                        )
                        onOrderUpdate(updatedOrder)
                    },
                    onDescriptionChange = { desc = it },
                    text = status
                )
            }
        }
    }
}

@Composable
fun DispatchDialog(
    onDismiss: () -> Unit,
    onConfirm: (Driver?) -> Unit,
    orderId: String,
    token: String?,
    onDescriptionChange: (String) -> Unit,
    address: String?,
) {
    var fetchedDriver by remember { mutableStateOf<Driver?>(null) }
    var desc by remember { mutableStateOf("") }
    LaunchedEffect(orderId) {
        fetchedDriver = fetchDriver(token)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dispatch Order") },
        text = {
            Column {
                Text("Selected Driver:")
                if (fetchedDriver != null) {
                    Text(("Id: " + fetchedDriver?.id) ?: "No Driver Available")
                    Text(text = ("Name: " + fetchedDriver!!.name) ?: "")
                } else {
                    Text("Loading driver...")
                }
                Text(text = ("Pickup Address: $address") ?: "", modifier = Modifier.padding(bottom = 16.dp))
                TextField(
                    value = desc,
                    onValueChange = {
                        desc = it
                        onDescriptionChange(it)
                    },
                    label = { Text("Description") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(fetchedDriver) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                ),
                enabled = fetchedDriver != null
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ShippingDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    text: String,
) {

    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$text Order") },
        text = {
            Column {
                TextField(
                    value = desc,
                    onValueChange = {
                        desc = it
                        onDescriptionChange(it)
                    },
                    label = { Text("Description") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                ),
            ) {
                Text(text)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
            ) {
                Text("Cancel")
            }
        }
    )
}

suspend fun dispatchOrder(token: String?, id: String?, dispatcherId: String?, dispatchedToId: String?, pickupAddress: String?, note: String, dateCreated: String) {
    val client = createTrustAllSslSocketFactory()
    val requestBody = """
        {
            "Id": "$id",
            "DispatcherId": "$dispatcherId",
            "DispatchedToId": "$dispatchedToId",
            "PickupAddress": "$pickupAddress",
            "Note": "$note",
            "DateCreated": "$dateCreated"
        }
    """.trimIndent()
    println(requestBody)
    val request = Request.Builder()
        .url("https://10.0.2.2:7061/api/Order/DispatchOrder")
        .addHeader("Authorization", "Bearer $token")
        .post(requestBody.toRequestBody("application/json".toMediaTypeOrNull()))
        .build()
    try {
        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }
        println("Error: ${response.message}")
    } catch (e: IOException) {
        println("Error")
        e.printStackTrace()
    }
}

suspend fun shippingOrder(token: String?, id: String?, dispatcherId: String?, dispatchedToId: String?, pickupAddress: String?, note: String, dateCreated: String, status: String) {
    val client = createTrustAllSslSocketFactory()
    val requestBody = """
        {
            "Id": "$id",
            "DispatcherId": "$dispatcherId",
            "DispatchedToId": "$dispatchedToId",
            "PickupAddress": "$pickupAddress",
            "Note": "$note",
            "DateCreated": "$dateCreated",
            "Status": "$status"
        }
    """.trimIndent()
    println(requestBody)
    val request = Request.Builder()
        .url("https://10.0.2.2:7061/api/Order/ShippingOrder")
        .addHeader("Authorization", "Bearer $token")
        .post(requestBody.toRequestBody("application/json".toMediaTypeOrNull()))
        .build()
    try {
        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }
        println("Error: ${response.message}")
    } catch (e: IOException) {
        println("Error")
        e.printStackTrace()
    }
}

suspend fun fetchDriver(token: String?): Driver? {
    // don't do this is production
    val client = createTrustAllSslSocketFactory()
    val request = Request.Builder()
        .url("https://10.0.2.2:7061/api/Users/getDriver")
        .addHeader("Authorization", "Bearer $token")
        .build()

    return withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let {
                    Gson().fromJson(it, Driver::class.java)
                }
            } else {
                println("Error: ${response.code}")
                null
            }
        } catch (e: Exception) {
            println("Error fetching driver: ${e.message}")
            null
        }
    }
}

suspend fun fetchOrders(id: String?, token: String?): List<Order> {
    // don't do this is production
    val client = createTrustAllSslSocketFactory()
    val request = Request.Builder()
        .url("https://10.0.2.2:7061/api/Order/getAndroidOrders?userId=$id")
        .addHeader("Authorization", "Bearer $token")
        .build()

    return withContext(Dispatchers.IO) {
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val responseBody = response.body?.string()
            println("Response: $responseBody")
            parseOrders(responseBody)
        } else {
            println("Error: ${response.code}")
            emptyList()
        }
    }
}

data class Driver(
    val id: String,
    val name: String,
)

data class Order(
    val id: String,
    val orderId: String,
    val amountTotal: Long,
    val dateCreated: String,
    val currency: String,
    val customerDetails: CustomerDetails,
    val productDetails: List<ProductDetails>,
    val shippingDetails: List<ShippingDetails>,
    val status: String,
    val paymentStatus: Int
)

data class CustomerDetails(
    val firstName: String,
    val lastName: String,
    val address: String,
    val zipCode: Int,
    val city: String,
    val state: String,
    val email: String,
    val phoneNumber: String
)

data class ProductDetails(
    val productId: String,
    val productName: String,
    val color: String,
    val memory: String,
    val storage: String,
    val quantity: Int,
    val image: String
)

data class ShippingDetails(
    val dispatcherId: String?,
    val dispatchedToId: String?,
    val pickupAddress: String?,
    val note: String,
    val dateCreated: String
)

data class ShippingDetails2(
    val dispatcherId: String?,
    val dispatchedToId: String?,
    val pickupAddress: String?,
    val note: String,
    val dateCreated: String,
    val status: String
)

fun parseOrders(json: String?): List<Order> {
    val gson = Gson()
    return if (json != null) {
        val listType = object : TypeToken<List<Order>>() {}.type
        gson.fromJson(json, listType)
    } else {
        emptyList()
    }
}

@SuppressLint("NewApi")
fun getCurrentDateTime(): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)
    return formatter.format(Instant.now())
}

@SuppressLint("NewApi")
fun convertToVietnamTime(utcDateTime: String): String {
    val instant = Instant.parse(utcDateTime)

    val vietnamZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    val vietnamTime = ZonedDateTime.ofInstant(instant, vietnamZoneId)

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return formatter.format(vietnamTime)
}

suspend fun makeApiCall(emailOrPhone: String, password: String, context: Context, onWarning: (String) -> Unit) {
    // don't do this is production
    val client = createTrustAllSslSocketFactory()
    val requestBody = """
        {
            "EmailOrPhone": "$emailOrPhone",
            "Password": "$password"
        }
    """.trimIndent()

    val request = Request.Builder()
        .url("https://10.0.2.2:7061/api/Users/loginAndroid")
        .post(requestBody.toRequestBody("application/json".toMediaTypeOrNull()))
        .build()

    try {
        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        when (response.code) {
            200 -> {
                val responseBody = response.body?.string()
                saveToken(context, responseBody)
                onWarning("")
            }
            204 -> {
                onWarning("Can't find user")
            }
            401 -> {
                onWarning("User is not verified")
            }
            400 -> {
                onWarning("Wrong email or password")
            }
            else -> {
                println("Error: ${response.message}")
            }
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

fun saveToken(context: Context, token: String?) {
    val sharedPref = context.getSharedPreferences("app_prefs", MODE_PRIVATE)
    with(sharedPref.edit()) {
        putString("auth_token", token)
        apply()
    }
}

fun deleteToken(context: Context) {
    val sharedPref = context.getSharedPreferences("app_prefs", MODE_PRIVATE)
    with(sharedPref.edit()) {
        remove("auth_token")
        apply()
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AppleAndroidTheme {
    }
}
