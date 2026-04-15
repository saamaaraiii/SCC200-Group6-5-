package com.example.transitnavproject

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import kotlinx.coroutines.delay
import kotlin.math.*
import java.util.Locale
import java.text.SimpleDateFormat
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow

// ==================== DATA CLASSES ====================

data class UserCredentials(
    val username: String,
    val email: String,
    val phone: String,
    val countryCode: String,
    val password: String
)

data class TrainStop(
    val name: String,
    val timeArrival: String,
    val status: String = "On Time"
)

data class LiveTrackingData(
    val currentStation: String,
    val operator: String,
    val nextStation: String,
    val destination: String,
    val upcomingStops: List<TrainStop>,
    val progress: Float = 0f,
    val distance: String = "12.5 km",
    val latitude: Double = 54.0481,
    val longitude: Double = -2.8007,
    val speed: String = "125 km/h"
)

data class BookedJourney(
    val id: String,
    val reference: String,
    val from: String,
    val to: String,
    val date: String,
    val departureTime: String,
    val arrivalTime: String,
    val duration: Int,
    val railcard: String,
    val qrCodeContent: String
)

data class SavedLocation(
    val id: Int,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val icon: String = "📍"
)

data class SavedWalkRoute(
    val id: String,
    val origin: SavedLocation,
    val destination: SavedLocation,
    val distanceMeters: Int,
    val durationMinutes: Int,
    val coordinates: List<Pair<Double, Double>>,
    val timestamp: Long = System.currentTimeMillis()
)

data class BusStop(
    val id: Int,
    val name: String,
    val code: String,
    val city: String,
    val distance: String,
    val nextDeparture: String,
    val routes: List<String>,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

data class TrainStation(
    val id: Int,
    val name: String,
    val code: String,
    val city: String,
    val distance: String,
    val nextDeparture: String,
    val platforms: Int,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

data class Departure(
    val id: String,
    val lineCode: String,
    val destination: String,
    val operator: String,
    val status: String,
    val timeRemaining: String,
    val bgColor: ComposeColor
)

data class AppUser(
    val name: String,
    val email: String,
    val phone: String,
    val studentId: String = "LU-2026-X99",
    val university: String = "Lancaster University",
    val status: String = "Active Student"
)

data class Attraction(
    val id: Int,
    val name: String,
    val type: String,
    val rating: Double,
    val reviews: Int,
    val priceRange: String,
    val distance: String,
    val description: String,
    val tags: List<String>
)

data class RouteLeg(
    val transport: String,
    val origin: String,
    val destination: String,
    val duration: Int,
    val departureTime: String,
    val arrivalTime: String,
    val platform: String? = null
)

data class Journey(
    val origin: String,
    val destination: String,
    val reference: String,
    val date: String,
    val totalDuration: Int,
    val railcard: String,
    val legs: List<RouteLeg>
)

data class WalkingRoute(
    val origin: SavedLocation?,
    val destination: SavedLocation?,
    val distanceMeters: Int = 0,
    val durationMinutes: Int = 0,
    val coordinates: List<Pair<Double, Double>> = emptyList()
)

data class FrequentDestination(
    val name: String,
    val icon: String,
    val visits: Int,
    val lastVisit: String
)

data class Contact(
    val id: Int,
    val name: String,
    val phone: String
)

data class MapMarker(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val type: String,
    val icon: String
)

data class NavigationItem(
    val icon: ImageVector,
    val label: String,
    val index: Int
)

// ==================== ENUMS ====================

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    CHINESE("zh", "Chinese"),
    JAPANESE("ja", "Japanese"),
    GERMAN("de", "German"),
    FRENCH("fr", "French"),
    SPANISH("es", "Spanish"),
    KOREAN("ko", "Korean"),
    PORTUGUESE("pt", "Portuguese"),
    ARABIC("ar", "Arabic"),
    RUSSIAN("ru", "Russian"),
    ITALIAN("it", "Italian"),
    DUTCH("nl", "Dutch"),
    SWEDISH("sv", "Swedish"),
    POLISH("pl", "Polish"),
    TURKISH("tr", "Turkish")
}

enum class OverlayState {
    NONE, PLANNER, SETTINGS
}

enum class AccountScreen {
    MAIN, SAVED_STOPS, SAVED_TRIPS, NOTIFICATIONS
}

enum class Theme {
    DARK, LIGHT, SYSTEM
}

enum class JourneyStatus {
    PENDING, CONFIRMED, BOARDED, IN_TRANSIT, COMPLETED, CANCELLED
}

enum class TransportType {
    TRAIN, BUS, WALK, TAXI, COACH
}

// ==================== USER PREFERENCES MANAGER ====================

class UserPreferencesManager(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("neso_user_prefs", Context.MODE_PRIVATE)

    fun saveUser(credentials: UserCredentials) {
        sharedPreferences.edit().apply {
            putString("username", credentials.username)
            putString("email", credentials.email)
            putString("phone", credentials.phone)
            putString("country_code", credentials.countryCode)
            putString("password", credentials.password)
            putBoolean("is_logged_in", true)
        }.apply()
    }

    fun getUser(): UserCredentials? {
        val username = sharedPreferences.getString("username", null) ?: return null
        return UserCredentials(
            username = username,
            email = sharedPreferences.getString("email", "") ?: "",
            phone = sharedPreferences.getString("phone", "") ?: "",
            countryCode = sharedPreferences.getString("country_code", "+44") ?: "+44",
            password = sharedPreferences.getString("password", "") ?: ""
        )
    }

    fun isLoggedIn(): Boolean = sharedPreferences.getBoolean("is_logged_in", false)

    fun logout() {
        sharedPreferences.edit().apply {
            clear()
        }.apply()
    }

    fun saveFontSize(size: Float) {
        sharedPreferences.edit().apply {
            putFloat("font_size", size)
        }.apply()
    }

    fun getFontSize(): Float = sharedPreferences.getFloat("font_size", 1f)

    fun saveTheme(theme: String) {
        sharedPreferences.edit().apply {
            putString("theme", theme)
        }.apply()
    }

    fun getTheme(): String = sharedPreferences.getString("theme", "Dark") ?: "Dark"

    fun saveLanguage(language: String) {
        sharedPreferences.edit().apply {
            putString("language", language)
        }.apply()
    }

    fun getLanguage(): String = sharedPreferences.getString("language", "en") ?: "en"

    fun saveNotifications(enabled: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean("notifications", enabled)
        }.apply()
    }

    fun getNotifications(): Boolean = sharedPreferences.getBoolean("notifications", true)

    fun saveLocation(enabled: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean("location", enabled)
        }.apply()
    }

    fun getLocation(): Boolean = sharedPreferences.getBoolean("location", true)

    fun saveAnalytics(enabled: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean("analytics", enabled)
        }.apply()
    }

    fun getAnalytics(): Boolean = sharedPreferences.getBoolean("analytics", false)

    fun saveHighContrast(enabled: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean("high_contrast", enabled)
        }.apply()
    }

    fun getHighContrast(): Boolean = sharedPreferences.getBoolean("high_contrast", false)

    fun updateUserProfile(name: String, email: String, phone: String) {
        sharedPreferences.edit().apply {
            putString("full_name", name)
            putString("user_email", email)
            putString("user_phone", phone)
        }.apply()
    }

    fun getUserProfile(): Triple<String, String, String> {
        val name = sharedPreferences.getString("full_name", "Alex Traveller") ?: "Alex Traveller"
        val email = sharedPreferences.getString("user_email", "alex@example.com") ?: "alex@example.com"
        val phone = sharedPreferences.getString("user_phone", "+44 7700 900000") ?: "+44 7700 900000"
        return Triple(name, email, phone)
    }
}

// ==================== TRANSLATION MANAGER ====================

class TranslationManager {
    private var currentLanguage = AppLanguage.ENGLISH

    private val translations = mapOf(
        AppLanguage.ENGLISH to mapOf(
            "Welcome to Neso" to "Welcome to Neso",
            "Your transport companion" to "Your transport companion",
            "Login" to "Login",
            "Register" to "Register",
            "Sign Up" to "Sign Up",
            "Username" to "Username",
            "Email" to "Email",
            "Password" to "Password",
            "Phone" to "Phone",
            "Don't have an account?" to "Don't have an account?",
            "Already have an account?" to "Already have an account?",
            "Save Changes" to "Save Changes",
            "Log Out" to "Log Out",
            "Settings" to "Settings",
            "Personal Data" to "Personal Data",
            "Language" to "Language",
            "Appearance" to "Appearance",
            "Font Size" to "Font Size",
            "Tickets" to "Tickets",
            "Track" to "Track",
            "Full Name" to "Full Name",
            "Dark" to "Dark",
            "Light" to "Light",
            "System" to "System",
            "Welcome back" to "Welcome back",
            "Home" to "Home",
            "Map" to "Map",
            "Live Tracking" to "Live Tracking",
            "Currently at" to "Currently at",
            "Next Stop" to "Next Stop",
            "Speed" to "Speed",
            "Distance Remaining" to "Distance Remaining",
            "Account" to "Account",
            "Notifications" to "Notifications",
            "Location Services" to "Location Services",
            "Share Analytics" to "Share Analytics",
            "Discover Lancaster" to "Discover Lancaster",
            "TripAdvisor" to "TripAdvisor",
            "Emergency" to "Emergency",
            "Frequent Destinations" to "Frequent Destinations",
            "Announcements" to "Announcements",
            "Preferences" to "Preferences",
            "Quick Actions" to "Quick Actions",
            "Saved Trips" to "Saved Trips",
            "Saved Stops" to "Saved Stops",
            "Walking Routes" to "Walking Routes",
            "Bus Stops" to "Bus Stops",
            "Train Stations" to "Train Stations",
            "Accessibility" to "Accessibility",
            "High Contrast" to "High Contrast",
            "Enable VoiceOver" to "Enable VoiceOver",
            "Notification Settings" to "Notification Settings",
            "Journey Alerts" to "Journey Alerts",
            "Arrival Notifications" to "Arrival Notifications",
            "Delay Alerts" to "Delay Alerts",
            "Account Details" to "Account Details",
            "Student ID" to "Student ID",
            "University" to "University",
            "Route Saved" to "Route Saved",
            "Calculate Route" to "Calculate Route",
            "View on Map" to "View on Map",
            "Route Details" to "Route Details",
            "Add Location" to "Add Location",
            "Saved Locations" to "Saved Locations",
            "Route History" to "Route History"
        )
    )

    fun setLanguage(language: AppLanguage) {
        currentLanguage = language
    }

    fun translate(text: String): String {
        val langTranslations = translations[currentLanguage] ?: translations[AppLanguage.ENGLISH] ?: emptyMap()
        return langTranslations[text] ?: text
    }
}

val translationManager = TranslationManager()

// ==================== VALIDATION UTILITIES ====================

object ValidationUtils {
    fun isValidEmail(email: String): Boolean {
        return email.contains("@") && email.contains(".") && email.length > 5
    }

    fun isValidPassword(password: String): Boolean {
        return password.length >= 6
    }

    fun isValidPhone(phone: String): Boolean {
        return phone.length >= 10 && phone.all { it.isDigit() || it == '+' }
    }

    fun isValidUsername(username: String): Boolean {
        return username.length >= 3 && username.all { it.isLetterOrDigit() || it == '_' }
    }
}

// ==================== ROUTE DATABASE ====================

class RouteDatabase {
    private val savedRoutes = mutableMapOf<String, SavedWalkRoute>()

    fun saveRoute(route: SavedWalkRoute) {
        savedRoutes[route.id] = route
    }

    fun getRoute(id: String): SavedWalkRoute? = savedRoutes[id]

    fun getAllRoutes(): List<SavedWalkRoute> = savedRoutes.values.toList()

    fun deleteRoute(id: String) {
        savedRoutes.remove(id)
    }
}

// ==================== LOCATION LIBRARY ====================

class LocationLibrary {
    val locations = listOf(
        SavedLocation(1, "Lancaster University", "Bailrigg, Lancaster", 54.0137, -2.6345, "University", "🎓"),
        SavedLocation(2, "Lancaster Station", "Station Road, Lancaster", 54.0481, -2.8007, "Station", "🚂"),
        SavedLocation(3, "Preston Station", "Fishergate, Preston", 53.7429, -2.2406, "Station", "🚂"),
        SavedLocation(4, "Manchester Piccadilly", "Piccadilly, Manchester", 53.4808, -2.2426, "Station", "🚂"),
        SavedLocation(5, "Liverpool Station", "Lime Street, Liverpool", 53.4069, -2.9789, "Station", "🚂"),
        SavedLocation(6, "Lancaster City Centre", "Market Square, Lancaster", 54.0476, -2.8011, "City", "🏙️"),
        SavedLocation(7, "Williamson Park", "Quernmore Road, Lancaster", 54.0228, -2.6411, "Park", "🌳"),
        SavedLocation(8, "Lancaster Castle", "Castle Hill, Lancaster", 54.0475, -2.8039, "Museum", "🏰"),
        SavedLocation(9, "Blackpool Tower", "The Seafront, Blackpool", 53.8149, -3.0585, "Attraction", "🗼"),
        SavedLocation(10, "Home", "Shared Location", 54.0137, -2.6345, "Home", "🏠")
    )

    fun searchLocations(query: String): List<SavedLocation> {
        if (query.isEmpty()) return emptyList()
        return locations.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.address.contains(query, ignoreCase = true)
        }
    }
}

val locationLibrary = LocationLibrary()
val routeDatabase = RouteDatabase()

// ==================== THEME PALETTE ====================

object TransitPalette {
    val DeepNavy = ComposeColor(0xFF0A1320)
    val SurfaceBlue = ComposeColor(0xFF1A2B4A)
    val InputGrey = ComposeColor(0xFF243550)
    val PrimaryBlue = ComposeColor(0xFF3B82F6)
    val TaxiYellow = ComposeColor(0xFFFFD700)
    val TripAdvisorGreen = ComposeColor(0xFF34E0A1)
    val SuccessGreen = ComposeColor(0xFF4CAF50)
    val ErrorRed = ComposeColor(0xFFFF5252)
    val TextHigh = ComposeColor.White
    val TextLow = ComposeColor(0xFF9CA3AF)
    val BorderDark = ComposeColor(0xFF334155)
    val BorderSubtle = ComposeColor(0xFF2D3A52)
    val BorderLight = ComposeColor(0xFF3A4B63)
}

// ==================== STRINGS ====================

object Strings {
    const val DISCOVER = "Discover Lancaster"
    const val TRIP_ADVISOR = "TripAdvisor"
    const val NEARBY_TAXIS = "Nearby Taxis"
    const val REQUEST_TAXI = "Request Nearby Taxi"
    const val DIRECTIONS = "Directions"
    const val DEPARTURES = "Departures"
    const val STATION = "STATION"
    const val STOP = "STOP"
}

// ==================== SAVED LOCATIONS MANAGER ====================

class SavedLocationsManager(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("saved_locations", Context.MODE_PRIVATE)
    private val savedLocations = mutableMapOf<Int, SavedLocation>()

    fun saveLocation(location: SavedLocation) {
        savedLocations[location.id] = location
    }

    fun getLocation(id: Int): SavedLocation? = savedLocations[id]

    fun getAllLocations(): List<SavedLocation> = savedLocations.values.toList()

    fun deleteLocation(id: Int) {
        savedLocations.remove(id)
    }
}

// ==================== MAIN ACTIVITY ====================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val sharedPrefs = getSharedPreferences("transit_navigator_2026", Context.MODE_PRIVATE)
            Configuration.getInstance().load(applicationContext, sharedPrefs)
        } catch (e: Exception) {
            println("Map config error: ${e.message}")
        }
        setContent {
            AppInitializer()
        }
    }
}

// ==================== APP INITIALIZER ====================

@Composable
fun AppInitializer() {
    val context = LocalContext.current
    val userPrefsManager = remember { UserPreferencesManager(context) }
    var isLoggedIn by remember { mutableStateOf(userPrefsManager.isLoggedIn()) }
    var currentUser by remember { mutableStateOf(userPrefsManager.getUser()) }
    var selectedLanguage by remember { mutableStateOf(AppLanguage.ENGLISH) }
    var activeTheme by remember { mutableStateOf(userPrefsManager.getTheme()) }
    var fontSizeMultiplier by remember { mutableFloatStateOf(userPrefsManager.getFontSize()) }
    var currentPlannedJourney by remember { mutableStateOf<Journey?>(null) }

    LaunchedEffect(selectedLanguage) {
        translationManager.setLanguage(selectedLanguage)
        userPrefsManager.saveLanguage(selectedLanguage.code)
    }

    LaunchedEffect(activeTheme) {
        userPrefsManager.saveTheme(activeTheme)
    }

    LaunchedEffect(fontSizeMultiplier) {
        userPrefsManager.saveFontSize(fontSizeMultiplier)
    }

    MaterialTheme {
        if (isLoggedIn && currentUser != null) {
            AppRootContainer(
                onLogout = {
                    userPrefsManager.logout()
                    isLoggedIn = false
                    currentUser = null
                },
                userPrefsManager = userPrefsManager,
                selectedLanguage = selectedLanguage,
                onLanguageChange = { selectedLanguage = it },
                isDark = activeTheme == "Dark",
                activeTheme = activeTheme,
                onThemeChange = { activeTheme = it },
                fontSizeMultiplier = fontSizeMultiplier,
                onFontSizeChange = { fontSizeMultiplier = it },
                currentPlannedJourney = currentPlannedJourney,
                onJourneyPlanned = { currentPlannedJourney = it }
            )
        } else {
            AuthenticationScreen(
                userPrefsManager = userPrefsManager,
                onLoginSuccess = { user ->
                    currentUser = user
                    isLoggedIn = true
                }
            )
        }
    }
}

// ==================== AUTHENTICATION SCREENS ====================

@Composable
fun AuthenticationScreen(
    userPrefsManager: UserPreferencesManager,
    onLoginSuccess: (UserCredentials) -> Unit
) {
    var isLoginMode by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        TransitPalette.DeepNavy,
                        TransitPalette.SurfaceBlue
                    )
                )
            )
    ) {
        if (isLoginMode) {
            LoginScreen(
                onLoginSuccess = onLoginSuccess,
                onSwitchToRegister = { isLoginMode = false },
                userPrefsManager = userPrefsManager
            )
        } else {
            RegisterScreen(
                onRegisterSuccess = { credentials ->
                    userPrefsManager.saveUser(credentials)
                    isLoginMode = true
                },
                onSwitchToLogin = { isLoginMode = true }
            )
        }
    }
}

// ==================== LOGIN SCREEN ====================

@Composable
fun LoginScreen(
    onLoginSuccess: (UserCredentials) -> Unit,
    onSwitchToRegister: () -> Unit,
    userPrefsManager: UserPreferencesManager
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 60.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                "NESO",
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TransitPalette.PrimaryBlue,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            Text(
                translationManager.translate("Welcome to Neso"),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = ComposeColor.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            Text(
                translationManager.translate("Your transport companion"),
                fontSize = 14.sp,
                color = TransitPalette.TextLow,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 40.dp)
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(TransitPalette.SurfaceBlue),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        translationManager.translate("Login"),
                        color = ComposeColor.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    if (errorMessage.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(TransitPalette.ErrorRed.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                errorMessage,
                                color = TransitPalette.ErrorRed,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    TextField(
                        value = username,
                        onValueChange = { username = it; errorMessage = "" },
                        placeholder = { Text(translationManager.translate("Username"), color = TransitPalette.TextLow) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = TransitPalette.InputGrey,
                            unfocusedContainerColor = TransitPalette.InputGrey,
                            focusedIndicatorColor = ComposeColor.Transparent,
                            unfocusedIndicatorColor = ComposeColor.Transparent,
                            focusedTextColor = ComposeColor.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = TransitPalette.PrimaryBlue) },
                        singleLine = true,
                        enabled = !isLoading
                    )

                    Spacer(Modifier.height(16.dp))

                    TextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = "" },
                        placeholder = { Text(translationManager.translate("Password"), color = TransitPalette.TextLow) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = TransitPalette.InputGrey,
                            unfocusedContainerColor = TransitPalette.InputGrey,
                            focusedIndicatorColor = ComposeColor.Transparent,
                            unfocusedIndicatorColor = ComposeColor.Transparent,
                            focusedTextColor = ComposeColor.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = TransitPalette.PrimaryBlue) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }, enabled = !isLoading) {
                                Icon(
                                    if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    null,
                                    tint = TransitPalette.TextLow
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !isLoading
                    )

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = {
                            when {
                                username.isBlank() -> errorMessage = "Username is required"
                                password.isBlank() -> errorMessage = "Password is required"
                                else -> {
                                    isLoading = true
                                    val credentials = UserCredentials(
                                        username = username,
                                        email = "user@example.com",
                                        phone = "+44 1234567890",
                                        countryCode = "+44",
                                        password = password
                                    )
                                    userPrefsManager.saveUser(credentials)
                                    onLoginSuccess(credentials)
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(TransitPalette.PrimaryBlue),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = ComposeColor.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                translationManager.translate("Login"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = ComposeColor.White
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            translationManager.translate("Don't have an account?"),
                            color = TransitPalette.TextLow,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            translationManager.translate("Register"),
                            color = TransitPalette.PrimaryBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(enabled = !isLoading) { onSwitchToRegister() }
                        )
                    }
                }
            }
        }
    }
}

// ==================== REGISTER SCREEN ====================

@Composable
fun RegisterScreen(
    onRegisterSuccess: (UserCredentials) -> Unit,
    onSwitchToLogin: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf("+44") }
    var errorMessage by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val countryCodes = listOf("+44", "+1", "+91", "+86", "+81", "+33", "+49", "+39", "+34", "+61")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 40.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                "NESO",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TransitPalette.PrimaryBlue,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            Text(
                translationManager.translate("Register"),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = ComposeColor.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(TransitPalette.SurfaceBlue),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
            ) {
                Column(Modifier.padding(24.dp)) {
                    if (errorMessage.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(TransitPalette.ErrorRed.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                errorMessage,
                                color = TransitPalette.ErrorRed,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    TextField(
                        value = username,
                        onValueChange = { username = it; errorMessage = "" },
                        placeholder = { Text(translationManager.translate("Username"), color = TransitPalette.TextLow) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = TransitPalette.InputGrey,
                            unfocusedContainerColor = TransitPalette.InputGrey,
                            focusedIndicatorColor = ComposeColor.Transparent,
                            unfocusedIndicatorColor = ComposeColor.Transparent,
                            focusedTextColor = ComposeColor.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = TransitPalette.PrimaryBlue) },
                        singleLine = true,
                        enabled = !isLoading
                    )

                    Spacer(Modifier.height(12.dp))

                    TextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = "" },
                        placeholder = { Text(translationManager.translate("Email"), color = TransitPalette.TextLow) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = TransitPalette.InputGrey,
                            unfocusedContainerColor = TransitPalette.InputGrey,
                            focusedIndicatorColor = ComposeColor.Transparent,
                            unfocusedIndicatorColor = ComposeColor.Transparent,
                            focusedTextColor = ComposeColor.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        leadingIcon = { Icon(Icons.Default.Email, null, tint = TransitPalette.PrimaryBlue) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        enabled = !isLoading
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(0.35f)) {
                            var expandedCountry by remember { mutableStateOf(false) }
                            Button(
                                onClick = { expandedCountry = !expandedCountry },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(TransitPalette.InputGrey),
                                shape = RoundedCornerShape(14.dp),
                                enabled = !isLoading
                            ) {
                                Text(countryCode, color = ComposeColor.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ExpandMore, null, tint = ComposeColor.White, modifier = Modifier.size(20.dp))
                            }

                            if (expandedCountry) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 56.dp)
                                        .background(TransitPalette.SurfaceBlue, RoundedCornerShape(8.dp))
                                        .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .heightIn(max = 200.dp)
                                ) {
                                    LazyColumn {
                                        items(countryCodes) { code ->
                                            Surface(
                                                onClick = {
                                                    countryCode = code
                                                    expandedCountry = false
                                                },
                                                color = ComposeColor.Transparent,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    code,
                                                    color = ComposeColor.White,
                                                    modifier = Modifier.padding(12.dp),
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        TextField(
                            value = phone,
                            onValueChange = { phone = it; errorMessage = "" },
                            placeholder = { Text(translationManager.translate("Phone"), color = TransitPalette.TextLow) },
                            modifier = Modifier
                                .weight(0.65f)
                                .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = TransitPalette.InputGrey,
                                unfocusedContainerColor = TransitPalette.InputGrey,
                                focusedIndicatorColor = ComposeColor.Transparent,
                                unfocusedIndicatorColor = ComposeColor.Transparent,
                                focusedTextColor = ComposeColor.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            leadingIcon = { Icon(Icons.Default.Phone, null, tint = TransitPalette.PrimaryBlue) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            enabled = !isLoading
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    TextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = "" },
                        placeholder = { Text(translationManager.translate("Password"), color = TransitPalette.TextLow) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = TransitPalette.InputGrey,
                            unfocusedContainerColor = TransitPalette.InputGrey,
                            focusedIndicatorColor = ComposeColor.Transparent,
                            unfocusedIndicatorColor = ComposeColor.Transparent,
                            focusedTextColor = ComposeColor.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = TransitPalette.PrimaryBlue) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }, enabled = !isLoading) {
                                Icon(
                                    if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    null,
                                    tint = TransitPalette.TextLow
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !isLoading
                    )

                    Spacer(Modifier.height(12.dp))

                    TextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMessage = "" },
                        placeholder = { Text("Confirm Password", color = TransitPalette.TextLow) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = TransitPalette.InputGrey,
                            unfocusedContainerColor = TransitPalette.InputGrey,
                            focusedIndicatorColor = ComposeColor.Transparent,
                            unfocusedIndicatorColor = ComposeColor.Transparent,
                            focusedTextColor = ComposeColor.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = TransitPalette.PrimaryBlue) },
                        trailingIcon = {
                            IconButton(onClick = { showConfirmPassword = !showConfirmPassword }, enabled = !isLoading) {
                                Icon(
                                    if (showConfirmPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    null,
                                    tint = TransitPalette.TextLow
                                )
                            }
                        },
                        visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !isLoading
                    )

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = {
                            when {
                                username.isBlank() -> errorMessage = "Username is required"
                                !ValidationUtils.isValidUsername(username) -> errorMessage = "Username must be at least 3 characters"
                                email.isBlank() -> errorMessage = "Email is required"
                                !ValidationUtils.isValidEmail(email) -> errorMessage = "Invalid email format"
                                phone.isBlank() -> errorMessage = "Phone is required"
                                !ValidationUtils.isValidPhone(phone) -> errorMessage = "Invalid phone format"
                                password.isBlank() -> errorMessage = "Password is required"
                                !ValidationUtils.isValidPassword(password) -> errorMessage = "Password must be at least 6 characters"
                                password != confirmPassword -> errorMessage = "Passwords do not match"
                                else -> {
                                    isLoading = true
                                    val credentials = UserCredentials(
                                        username = username,
                                        email = email,
                                        phone = phone,
                                        countryCode = countryCode,
                                        password = password
                                    )
                                    onRegisterSuccess(credentials)
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(TransitPalette.PrimaryBlue),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = ComposeColor.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                translationManager.translate("Sign Up"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = ComposeColor.White
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            translationManager.translate("Already have an account?"),
                            color = TransitPalette.TextLow,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            translationManager.translate("Login"),
                            color = TransitPalette.PrimaryBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(enabled = !isLoading) { onSwitchToLogin() }
                        )
                    }
                }
            }
        }
    }
}

// ==================== APP ROOT CONTAINER ====================

@Composable
fun AppRootContainer(
    onLogout: () -> Unit,
    userPrefsManager: UserPreferencesManager,
    selectedLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    isDark: Boolean,
    activeTheme: String,
    onThemeChange: (String) -> Unit,
    fontSizeMultiplier: Float,
    onFontSizeChange: (Float) -> Unit,
    currentPlannedJourney: Journey?,
    onJourneyPlanned: (Journey?) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val appBg = if (isDark) TransitPalette.DeepNavy else ComposeColor(0xFFF2F5F9)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = appBg,
            bottomBar = {
                NavigationBar(
                    containerColor = if (isDark) ComposeColor(0xFF0A1420) else ComposeColor.White,
                    tonalElevation = 10.dp
                ) {
                    val navigationItems = listOf(
                        NavigationItem(Icons.Default.Home, translationManager.translate("Home"), 0),
                        NavigationItem(Icons.Default.Map, translationManager.translate("Map"), 1),
                        NavigationItem(Icons.AutoMirrored.Filled.DirectionsWalk, translationManager.translate("Walking Routes"), 2),
                        NavigationItem(Icons.Default.Search, "Search Trains", 3),
                        NavigationItem(Icons.Default.ConfirmationNumber, translationManager.translate("Tickets"), 4),
                        NavigationItem(Icons.Default.Person, translationManager.translate("Account"), 5)
                    )

                    navigationItems.forEach { item ->
                        NavigationBarItem(
                            selected = selectedTab == item.index,
                            onClick = { selectedTab = item.index },
                            icon = {
                                Icon(
                                    item.icon,
                                    null,
                                    tint = if (selectedTab == item.index) TransitPalette.PrimaryBlue else TransitPalette.TextLow
                                )
                            },
                            label = {
                                Text(
                                    item.label,
                                    color = if (selectedTab == item.index) TransitPalette.PrimaryBlue else TransitPalette.TextLow,
                                    fontSize = 10.sp
                                )
                            }
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (selectedTab) {
                    0 -> MainDashboardScreen(
                        isDark = isDark,
                        fontSizeMultiplier = fontSizeMultiplier,
                        userPrefsManager = userPrefsManager
                    )
                    1 -> LiveMapScreen(isDark = isDark, fontSizeMultiplier = fontSizeMultiplier)
                    2 -> WalkingRouteScreen(isDark = isDark, fontSizeMultiplier = fontSizeMultiplier)
                    3 -> SearchTrainsScreen(
                        isDark = isDark,
                        fontSizeMultiplier = fontSizeMultiplier,
                        onJourneyPlanned = onJourneyPlanned
                    )
                    4 -> TicketsScreen(isDark = isDark, fontSizeMultiplier = fontSizeMultiplier)
                    5 -> AccountScreen(
                        isDark = isDark,
                        onLogout = onLogout,
                        userPrefsManager = userPrefsManager,
                        onLanguageChange = onLanguageChange,
                        selectedLanguage = selectedLanguage,
                        fontSizeMultiplier = fontSizeMultiplier,
                        onFontSizeChange = onFontSizeChange,
                        activeTheme = activeTheme,
                        onThemeChange = onThemeChange
                    )
                }
            }
        }

        // Draggable SMS Button
        if (currentPlannedJourney != null) {
            DraggableSmsButton(
                currentJourney = currentPlannedJourney,
                isDark = isDark,
                fontSizeMultiplier = fontSizeMultiplier
            )
        }
    }
}
// ==================== MAIN DASHBOARD SCREEN ====================

@Composable
fun MainDashboardScreen(
    isDark: Boolean,
    fontSizeMultiplier: Float,
    userPrefsManager: UserPreferencesManager
) {
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) TransitPalette.DeepNavy else ComposeColor(0xFFF2F5F9))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            val (userName, _, _) = userPrefsManager.getUserProfile()
            Card(
                colors = CardDefaults.cardColors(cardColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        translationManager.translate("Welcome back"),
                        color = TransitPalette.TextLow,
                        fontSize = (12 * fontSizeMultiplier).sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        userName,
                        color = textColor,
                        fontSize = (24 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        translationManager.translate("Your transport companion"),
                        color = TransitPalette.TextLow,
                        fontSize = (13 * fontSizeMultiplier).sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            TripAdvisorSection(isDark, fontSizeMultiplier)
        }

        item {
            Spacer(Modifier.height(24.dp))
            QuickActionsPanel(isDark, fontSizeMultiplier)
        }

        item {
            Spacer(Modifier.height(24.dp))
            EmergencyPanel(isDark, fontSizeMultiplier)
        }

        item {
            Spacer(Modifier.height(24.dp))
            FrequentDestinationsPanel(isDark, fontSizeMultiplier)
        }

        item {
            Spacer(Modifier.height(24.dp))
            AnnouncementsPanel(isDark, fontSizeMultiplier)
        }

        item {
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ==================== LIVE MAP SCREEN ====================

@Composable
fun LiveMapScreen(
    isDark: Boolean,
    fontSizeMultiplier: Float
) {
    var liveTrackingData by remember { mutableStateOf<LiveTrackingData?>(null) }
    var isTracking by remember { mutableStateOf(false) }
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White

    LaunchedEffect(isTracking) {
        if (isTracking) {
            while (isTracking) {
                delay(2000)
                liveTrackingData = generateLiveTrackingData()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) TransitPalette.DeepNavy else ComposeColor(0xFFF2F5F9))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    translationManager.translate("Live Tracking"),
                    color = textColor,
                    fontSize = (24 * fontSizeMultiplier).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(cardColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Button(
                            onClick = { isTracking = !isTracking },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                if (isTracking) TransitPalette.ErrorRed else TransitPalette.SuccessGreen
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
                                null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isTracking) "Stop Tracking" else "Start Live Tracking",
                                color = ComposeColor.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = (14 * fontSizeMultiplier).sp
                            )
                        }
                    }
                }
            }

            if (liveTrackingData != null) {
                item {
                    LiveTrackingCard(liveTrackingData!!, isDark, fontSizeMultiplier)
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    InteractiveMapView(isDark = isDark)
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .background(TransitPalette.InputGrey, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Map,
                                null,
                                tint = TransitPalette.TextLow,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Start tracking to see live map",
                                color = TransitPalette.TextLow,
                                fontSize = (14 * fontSizeMultiplier).sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WalkingRouteScreen(isDark: Boolean, fontSizeMultiplier: Float) {

    // ==================== STATE ====================
    var originQuery by remember { mutableStateOf("") }
    var destinationQuery by remember { mutableStateOf("") }
    var selectedOrigin by remember { mutableStateOf<SavedLocation?>(null) }
    var selectedDestination by remember { mutableStateOf<SavedLocation?>(null) }
    var showOriginDropdown by remember { mutableStateOf(false) }
    var showDestinationDropdown by remember { mutableStateOf(false) }
    var calculatedRoute by remember { mutableStateOf<WalkingRoute?>(null) }
    var showRouteDetails by remember { mutableStateOf(false) }
    var selectedSubTab by remember { mutableIntStateOf(0) }

    // ✅ FIXED: moved OUTSIDE LazyColumn
    val context = LocalContext.current
    val locationsManager = remember { SavedLocationsManager(context) }
    val savedLocations = remember { mutableStateOf(locationLibrary.locations) }
    val allRoutes = remember { routeDatabase.getAllRoutes() }

    val originResults = locationLibrary.searchLocations(originQuery)
    val destinationResults = locationLibrary.searchLocations(destinationQuery)

    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val inputBg = if (isDark) TransitPalette.InputGrey else ComposeColor(0xFFE8EDF5)

    // ==================== UI ====================
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) TransitPalette.DeepNavy else ComposeColor(0xFFF2F5F9))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ==================== TABS ====================
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Calculate", "Saved", "History").forEachIndexed { index, label ->
                        Button(
                            onClick = { selectedSubTab = index },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            colors = ButtonDefaults.buttonColors(
                                if (selectedSubTab == index)
                                    TransitPalette.PrimaryBlue
                                else TransitPalette.InputGrey
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                label,
                                color = if (selectedSubTab == index)
                                    ComposeColor.White
                                else textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = (12 * fontSizeMultiplier).sp
                            )
                        }
                    }
                }
            }

            // ==================== TAB CONTENT ====================
            when (selectedSubTab) {

                // ==================== CALCULATE ====================
                0 -> {

                    item {
                        Text(
                            "🚶 Calculate Route",
                            color = textColor,
                            fontSize = (20 * fontSizeMultiplier).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(cardColor),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Column(Modifier.padding(20.dp)) {

                                // FROM FIELD
                                Text("From", color = TransitPalette.TextLow)
                                Spacer(Modifier.height(8.dp))

                                TextField(
                                    value = originQuery,
                                    onValueChange = {
                                        originQuery = it
                                        showOriginDropdown = it.isNotEmpty()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = {
                                        Icon(Icons.Default.LocationOn, null)
                                    }
                                )

                                Spacer(Modifier.height(16.dp))

                                // TO FIELD
                                Text("To", color = TransitPalette.TextLow)
                                Spacer(Modifier.height(8.dp))

                                TextField(
                                    value = destinationQuery,
                                    onValueChange = {
                                        destinationQuery = it
                                        showDestinationDropdown = it.isNotEmpty()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = {
                                        Icon(Icons.Default.LocationOn, null)
                                    }
                                )

                                Spacer(Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        if (selectedOrigin != null && selectedDestination != null) {
                                            calculatedRoute = calculateJourneyRoute(
                                                selectedOrigin!!,
                                                selectedDestination!!
                                            )
                                            showRouteDetails = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Directions, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Calculate Route")
                                }
                            }
                        }
                    }

                    if (calculatedRoute != null && showRouteDetails) {
                        item {
                            WalkingRouteDetailCard(
                                route = calculatedRoute!!,
                                isDark = isDark,
                                fontSizeMultiplier = fontSizeMultiplier
                            )
                        }
                    }
                }

                // ==================== SAVED ====================
                1 -> {

                    item {
                        Text(
                            "Saved Locations",
                            color = textColor,
                            fontSize = (20 * fontSizeMultiplier).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    items(savedLocations.value.size) { index ->
                        SavedLocationCard(
                            location = savedLocations.value[index],
                            isDark = isDark,
                            fontSizeMultiplier = fontSizeMultiplier,
                            onDelete = { }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                // ==================== HISTORY ====================
                2 -> {

                    item {
                        Text(
                            "Route History",
                            color = textColor,
                            fontSize = (20 * fontSizeMultiplier).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    if (allRoutes.isEmpty()) {
                        item {
                            Text("No routes yet", color = textColor)
                        }
                    } else {
                        items(allRoutes.size) { index ->
                            RouteHistoryCard(
                                route = allRoutes[index],
                                isDark = isDark,
                                fontSizeMultiplier = fontSizeMultiplier
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

// ==================== SEARCH TRAINS SCREEN ====================

@Composable
fun SearchTrainsScreen(
    isDark: Boolean,
    fontSizeMultiplier: Float,
    onJourneyPlanned: (Journey?) -> Unit
) {
    var originQuery by remember { mutableStateOf("") }
    var destinationQuery by remember { mutableStateOf("") }
    var selectedOrigin by remember { mutableStateOf<SavedLocation?>(null) }
    var selectedDestination by remember { mutableStateOf<SavedLocation?>(null) }
    var showOriginDropdown by remember { mutableStateOf(false) }
    var showDestinationDropdown by remember { mutableStateOf(false) }
    var journeyType by remember { mutableStateOf("Single") }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedRailcard by remember { mutableStateOf("No Railcard") }
    var showRailcardSelector by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Journey>?>(null) }
    var selectedJourney by remember { mutableStateOf<Journey?>(null) }

    val originResults = locationLibrary.searchLocations(originQuery)
    val destinationResults = locationLibrary.searchLocations(destinationQuery)
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val inputBg = if (isDark) TransitPalette.InputGrey else ComposeColor(0xFFE8EDF5)

    val railcardOptions = listOf(
        "No Railcard",
        "16-25 Railcard",
        "26-30 Railcard",
        "Senior Railcard",
        "Two Together Railcard",
        "Family & Friends Railcard",
        "Disabled Persons Railcard",
        "16-17 Saver",
        "Veterans Railcard"
    )

    if (selectedJourney != null) {
        JourneyDetailsScreen(
            journey = selectedJourney!!,
            onBack = { selectedJourney = null },
            isDark = isDark,
            fontSizeMultiplier = fontSizeMultiplier,
            onBooking = { bookedJourney ->
                onJourneyPlanned(selectedJourney)
                AppLogger.log("SearchTrains", "Booked journey: ${bookedJourney.reference}")
            }
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDark) TransitPalette.DeepNavy else ComposeColor(0xFFF2F5F9))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ArrowBack, null, tint = textColor, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Search Trains",
                            color = textColor,
                            fontSize = (24 * fontSizeMultiplier).sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                item {
                    Text(
                        "Plan Your Rail Journey",
                        color = textColor,
                        fontSize = (18 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    Text(
                        "Enter where you are going and customise your trip",
                        color = TransitPalette.TextLow,
                        fontSize = (13 * fontSizeMultiplier).sp,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(cardColor),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                "Where",
                                color = TransitPalette.TextLow,
                                fontSize = (12 * fontSizeMultiplier).sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Box {
                                TextField(
                                    value = originQuery,
                                    onValueChange = {
                                        originQuery = it
                                        showOriginDropdown = it.isNotEmpty()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = inputBg,
                                        unfocusedContainerColor = inputBg,
                                        focusedIndicatorColor = ComposeColor.Transparent,
                                        unfocusedIndicatorColor = ComposeColor.Transparent,
                                        focusedTextColor = textColor
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    placeholder = { Text("Type departure station", color = TransitPalette.TextLow) },
                                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size(18.dp)) },
                                    singleLine = true,
                                    label = { Text("Origin", fontSize = (11 * fontSizeMultiplier).sp) }
                                )

                                if (showOriginDropdown && originResults.isNotEmpty()) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 60.dp)
                                            .border(0.5.dp, TransitPalette.BorderSubtle, RoundedCornerShape(12.dp)),
                                        color = cardColor,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp)) {
                                            items(originResults) { location ->
                                                Surface(
                                                    onClick = {
                                                        selectedOrigin = location
                                                        originQuery = location.name
                                                        showOriginDropdown = false
                                                    },
                                                    color = ComposeColor.Transparent,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        location.name,
                                                        color = textColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = (13 * fontSizeMultiplier).sp,
                                                        modifier = Modifier.padding(12.dp)
                                                    )
                                                }
                                                HorizontalDivider(
                                                    color = TransitPalette.BorderSubtle.copy(alpha = 0.2f),
                                                    thickness = 0.5.dp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Box {
                                TextField(
                                    value = destinationQuery,
                                    onValueChange = {
                                        destinationQuery = it
                                        showDestinationDropdown = it.isNotEmpty()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = inputBg,
                                        unfocusedContainerColor = inputBg,
                                        focusedIndicatorColor = ComposeColor.Transparent,
                                        unfocusedIndicatorColor = ComposeColor.Transparent,
                                        focusedTextColor = textColor
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    placeholder = { Text("Type arrival station", color = TransitPalette.TextLow) },
                                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size(18.dp)) },
                                    singleLine = true,
                                    label = { Text("Destination", fontSize = (11 * fontSizeMultiplier).sp) }
                                )

                                if (showDestinationDropdown && destinationResults.isNotEmpty()) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 60.dp)
                                            .border(0.5.dp, TransitPalette.BorderSubtle, RoundedCornerShape(12.dp)),
                                        color = cardColor,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp)) {
                                            items(destinationResults) { location ->
                                                Surface(
                                                    onClick = {
                                                        selectedDestination = location
                                                        destinationQuery = location.name
                                                        showDestinationDropdown = false
                                                    },
                                                    color = ComposeColor.Transparent,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        location.name,
                                                        color = textColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = (13 * fontSizeMultiplier).sp,
                                                        modifier = Modifier.padding(12.dp)
                                                    )
                                                }
                                                HorizontalDivider(
                                                    color = TransitPalette.BorderSubtle.copy(alpha = 0.2f),
                                                    thickness = 0.5.dp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(24.dp))

                            Text(
                                "Local Operators (NOC)",
                                color = TransitPalette.TextLow,
                                fontSize = (12 * fontSizeMultiplier).sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                "ARCT, BLAC, KLCO, SCCU, SCMY, NUTT",
                                color = TransitPalette.TextLow,
                                fontSize = (11 * fontSizeMultiplier).sp,
                                modifier = Modifier.padding(bottom = 20.dp)
                            )

                            if (selectedOrigin != null && selectedDestination != null) {
                                Text(
                                    "Journey: ${selectedOrigin!!.name} → ${selectedDestination!!.name}",
                                    color = TransitPalette.PrimaryBlue,
                                    fontSize = (14 * fontSizeMultiplier).sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("Single", "Return", "Open Return").forEach { type ->
                                        Button(
                                            onClick = { journeyType = type },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                if (journeyType == type) TransitPalette.PrimaryBlue else TransitPalette.InputGrey
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                type,
                                                color = ComposeColor.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = (11 * fontSizeMultiplier).sp
                                            )
                                        }
                                    }
                                }

                                Text(
                                    "When",
                                    color = TransitPalette.TextLow,
                                    fontSize = (12 * fontSizeMultiplier).sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                Text(
                                    "Travel Dates",
                                    color = TransitPalette.TextLow,
                                    fontSize = (11 * fontSizeMultiplier).sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Outbound",
                                            color = TransitPalette.TextLow,
                                            fontSize = (10 * fontSizeMultiplier).sp
                                        )
                                        Button(
                                            onClick = { showDatePicker = true },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp),
                                            colors = ButtonDefaults.buttonColors(TransitPalette.InputGrey),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                SimpleDateFormat("EEE, d MMM yyyy", Locale.UK).format(selectedDate),
                                                color = ComposeColor.White,
                                                fontSize = (10 * fontSizeMultiplier).sp
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Return",
                                            color = TransitPalette.TextLow,
                                            fontSize = (10 * fontSizeMultiplier).sp
                                        )
                                        Button(
                                            onClick = { },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp),
                                            colors = ButtonDefaults.buttonColors(TransitPalette.InputGrey),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                "Not required",
                                                color = TransitPalette.TextLow,
                                                fontSize = (10 * fontSizeMultiplier).sp
                                            )
                                        }
                                    }
                                }

                                if (showDatePicker) {
                                    DatePickerModal(
                                        onDateSelected = { date ->
                                            selectedDate = date
                                            showDatePicker = false
                                        },
                                        onDismiss = { showDatePicker = false },
                                        isDark = isDark,
                                        fontSizeMultiplier = fontSizeMultiplier
                                    )
                                }

                                Text(
                                    "Railcard",
                                    color = TransitPalette.TextLow,
                                    fontSize = (12 * fontSizeMultiplier).sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                Button(
                                    onClick = { showRailcardSelector = !showRailcardSelector },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(TransitPalette.InputGrey),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "✓ $selectedRailcard",
                                        color = ComposeColor.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (12 * fontSizeMultiplier).sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.ExpandMore, null, tint = ComposeColor.White)
                                }

                                if (showRailcardSelector) {
                                    Spacer(Modifier.height(8.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(cardColor),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(0.5.dp, TransitPalette.BorderSubtle, RoundedCornerShape(12.dp))
                                    ) {
                                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                                            items(railcardOptions) { railcard ->
                                                Surface(
                                                    onClick = {
                                                        selectedRailcard = railcard
                                                        showRailcardSelector = false
                                                    },
                                                    color = ComposeColor.Transparent,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(12.dp)
                                                    ) {
                                                        if (railcard == selectedRailcard) {
                                                            Icon(Icons.Default.Check, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size(18.dp))
                                                            Spacer(Modifier.width(8.dp))
                                                        }
                                                        Text(
                                                            railcard,
                                                            color = textColor,
                                                            fontSize = (12 * fontSizeMultiplier).sp,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }
                                                HorizontalDivider(
                                                    color = TransitPalette.BorderSubtle.copy(alpha = 0.2f),
                                                    thickness = 0.5.dp
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        if (selectedOrigin != null && selectedDestination != null) {
                                            searchResults = getRecommendedJourneys(selectedOrigin!!, listOf(selectedDestination!!))
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(TransitPalette.PrimaryBlue),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Search Journeys",
                                        color = ComposeColor.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (14 * fontSizeMultiplier).sp
                                    )
                                }
                            }
                        }
                    }
                }

                if (searchResults != null) {
                    items(searchResults!!.size) { index ->
                        JourneyResultCard(
                            journey = searchResults!![index],
                            isDark = isDark,
                            fontSizeMultiplier = fontSizeMultiplier,
                            onSelect = { selectedJourney = it }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

// ==================== TICKETS SCREEN ====================

@Composable
fun TicketsScreen(
    isDark: Boolean,
    fontSizeMultiplier: Float
) {
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White

    val mockTickets = listOf(
        BookedJourney(
            id = "1",
            reference = "TR-8829",
            from = "Lancaster",
            to = "Manchester",
            date = "5 Mar 2026",
            departureTime = "14:30",
            arrivalTime = "15:45",
            duration = 75,
            railcard = "Student",
            qrCodeContent = "NESO-TR-8829-LAN-MAN"
        ),
        BookedJourney(
            id = "2",
            reference = "TR-8830",
            from = "Manchester",
            to = "Liverpool",
            date = "6 Mar 2026",
            departureTime = "10:00",
            arrivalTime = "10:35",
            duration = 35,
            railcard = "Student",
            qrCodeContent = "NESO-TR-8830-MAN-LIV"
        ),
        BookedJourney(
            id = "3",
            reference = "TR-8831",
            from = "Liverpool",
            to = "London Euston",
            date = "7 Mar 2026",
            departureTime = "12:15",
            arrivalTime = "16:30",
            duration = 255,
            railcard = "Student",
            qrCodeContent = "NESO-TR-8831-LIV-LON"
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) TransitPalette.DeepNavy else ComposeColor(0xFFF2F5F9))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                translationManager.translate("Tickets"),
                color = textColor,
                fontSize = (24 * fontSizeMultiplier).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        items(mockTickets.size) { index ->
            TicketCard(mockTickets[index], isDark, fontSizeMultiplier)
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ==================== ACCOUNT SCREEN ====================

@Composable
fun AccountScreen(
    isDark: Boolean,
    onLogout: () -> Unit,
    userPrefsManager: UserPreferencesManager,
    onLanguageChange: (AppLanguage) -> Unit,
    selectedLanguage: AppLanguage,
    fontSizeMultiplier: Float,
    onFontSizeChange: (Float) -> Unit,
    activeTheme: String,
    onThemeChange: (String) -> Unit
) {
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)
    var selectedSubTab by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) TransitPalette.DeepNavy else ComposeColor(0xFFF2F5F9))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    translationManager.translate("Settings"),
                    color = textColor,
                    fontSize = (24 * fontSizeMultiplier).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Main", "Personal", "Accessibility", "Notifications").forEachIndexed { index, label ->
                        Button(
                            onClick = { selectedSubTab = index },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            colors = ButtonDefaults.buttonColors(
                                if (selectedSubTab == index) TransitPalette.PrimaryBlue else TransitPalette.InputGrey
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                label,
                                color = if (selectedSubTab == index) ComposeColor.White else textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = (11 * fontSizeMultiplier).sp
                            )
                        }
                    }
                }
            }

            when (selectedSubTab) {
                0 -> {
                    item {
                        ThemeSettingsCard(isDark, fontSizeMultiplier, activeTheme, onThemeChange)
                        Spacer(Modifier.height(12.dp))
                    }

                    item {
                        LanguageSettingsCard(isDark, fontSizeMultiplier, selectedLanguage, onLanguageChange)
                        Spacer(Modifier.height(12.dp))
                    }

                    item {
                        FontSizeSettingsCard(isDark, fontSizeMultiplier, onFontSizeChange)
                        Spacer(Modifier.height(12.dp))
                    }

                    item {
                        PreferencesPanel(isDark, fontSizeMultiplier, userPrefsManager)
                        Spacer(Modifier.height(12.dp))
                    }

                    item {
                        AboutAppPanel(isDark, fontSizeMultiplier)
                        Spacer(Modifier.height(24.dp))
                    }

                    item {
                        Button(
                            onClick = onLogout,
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(TransitPalette.ErrorRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                translationManager.translate("Log Out"),
                                color = ComposeColor.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = (14 * fontSizeMultiplier).sp
                            )
                        }
                    }
                }

                1 -> {
                    item {
                        PersonalDataPanel(isDark, fontSizeMultiplier, userPrefsManager)
                    }
                }

                2 -> {
                    item {
                        AccessibilitySettingsPanel(isDark, fontSizeMultiplier, userPrefsManager)
                    }
                }

                3 -> {
                    item {
                        NotificationSettingsPanel(isDark, fontSizeMultiplier, userPrefsManager)
                    }
                }
            }
        }
    }
}

// ==================== PERSONAL DATA PANEL ====================

@Composable
fun PersonalDataPanel(
    isDark: Boolean,
    fontSizeMultiplier: Float,
    userPrefsManager: UserPreferencesManager
) {
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val inputBg = if (isDark) TransitPalette.InputGrey else ComposeColor(0xFFE8EDF5)

    val (initialName, initialEmail, initialPhone) = userPrefsManager.getUserProfile()
    var fullName by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    var phone by remember { mutableStateOf(initialPhone) }
    var saveSuccess by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            if (saveSuccess) {
                Card(
                    colors = CardDefaults.cardColors(TransitPalette.SuccessGreen.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = TransitPalette.SuccessGreen, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Changes saved successfully!",
                            color = TransitPalette.SuccessGreen,
                            fontSize = (12 * fontSizeMultiplier).sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(cardColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        translationManager.translate("Full Name"),
                        color = TransitPalette.PrimaryBlue,
                        fontSize = (12 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    TextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = inputBg,
                            unfocusedContainerColor = inputBg,
                            focusedIndicatorColor = ComposeColor.Transparent,
                            unfocusedIndicatorColor = ComposeColor.Transparent,
                            focusedTextColor = textColor
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = TransitPalette.PrimaryBlue) }
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        translationManager.translate("Email"),
                        color = TransitPalette.PrimaryBlue,
                        fontSize = (12 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    TextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = inputBg,
                            unfocusedContainerColor = inputBg,
                            focusedIndicatorColor = ComposeColor.Transparent,
                            unfocusedIndicatorColor = ComposeColor.Transparent,
                            focusedTextColor = textColor
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Email, null, tint = TransitPalette.PrimaryBlue) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        translationManager.translate("Phone"),
                        color = TransitPalette.PrimaryBlue,
                        fontSize = (12 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    TextField(
                        value = phone,
                        onValueChange = { phone = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = inputBg,
                            unfocusedContainerColor = inputBg,
                            focusedIndicatorColor = ComposeColor.Transparent,
                            unfocusedIndicatorColor = ComposeColor.Transparent,
                            focusedTextColor = textColor
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Phone, null, tint = TransitPalette.PrimaryBlue) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = {
                            userPrefsManager.updateUserProfile(fullName, email, phone)
                            saveSuccess = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(TransitPalette.PrimaryBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            translationManager.translate("Save Changes"),
                            color = ComposeColor.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = (14 * fontSizeMultiplier).sp
                        )
                    }
                }
            }
        }
    }
}

// ==================== ACCESSIBILITY SETTINGS PANEL ====================

@Composable
fun AccessibilitySettingsPanel(
    isDark: Boolean,
    fontSizeMultiplier: Float,
    userPrefsManager: UserPreferencesManager
) {
    var highContrast by remember { mutableStateOf(userPrefsManager.getHighContrast()) }
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White

    LaunchedEffect(highContrast) {
        userPrefsManager.saveHighContrast(highContrast)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(cardColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        translationManager.translate("Accessibility"),
                        color = TransitPalette.PrimaryBlue,
                        fontSize = (14 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Contrast, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                translationManager.translate("High Contrast"),
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = (13 * fontSizeMultiplier).sp
                            )
                        }
                        Switch(
                            checked = highContrast,
                            onCheckedChange = { highContrast = it },
                            modifier = Modifier.graphicsLayer(scaleX = 0.8f, scaleY = 0.8f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TransitPalette.PrimaryBlue,
                                checkedTrackColor = TransitPalette.PrimaryBlue.copy(alpha = 0.3f)
                            )
                        )
                    }

                    HorizontalDivider(
                        color = TransitPalette.BorderSubtle.copy(alpha = 0.2f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Card(
                        colors = CardDefaults.cardColors(TransitPalette.InputGrey),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                translationManager.translate("Enable VoiceOver"),
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = (12 * fontSizeMultiplier).sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                "To enable VoiceOver on your device:\n1. Go to Settings\n2. Select Accessibility\n3. Enable VoiceOver",
                                color = TransitPalette.TextLow,
                                fontSize = (11 * fontSizeMultiplier).sp
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(TransitPalette.PrimaryBlue),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "VoiceOver Guide",
                            color = ComposeColor.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = (12 * fontSizeMultiplier).sp
                        )
                    }
                }
            }
        }
    }
}

// ==================== NOTIFICATION SETTINGS PANEL ====================

@Composable
fun NotificationSettingsPanel(
    isDark: Boolean,
    fontSizeMultiplier: Float,
    userPrefsManager: UserPreferencesManager
) {
    var journeyAlerts by remember { mutableStateOf(true) }
    var arrivalNotifications by remember { mutableStateOf(true) }
    var delayAlerts by remember { mutableStateOf(true) }
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(cardColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        translationManager.translate("Notification Settings"),
                        color = TransitPalette.PrimaryBlue,
                        fontSize = (14 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Train, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                translationManager.translate("Journey Alerts"),
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = (13 * fontSizeMultiplier).sp
                            )
                        }
                        Switch(
                            checked = journeyAlerts,
                            onCheckedChange = { journeyAlerts = it },
                            modifier = Modifier.graphicsLayer(scaleX = 0.8f, scaleY = 0.8f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TransitPalette.PrimaryBlue,
                                checkedTrackColor = TransitPalette.PrimaryBlue.copy(alpha = 0.3f)
                            )
                        )
                    }

                    HorizontalDivider(
                        color = TransitPalette.BorderSubtle.copy(alpha = 0.2f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.CheckCircle, null, tint = TransitPalette.SuccessGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                translationManager.translate("Arrival Notifications"),
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = (13 * fontSizeMultiplier).sp
                            )
                        }
                        Switch(
                            checked = arrivalNotifications,
                            onCheckedChange = { arrivalNotifications = it },
                            modifier = Modifier.graphicsLayer(scaleX = 0.8f, scaleY = 0.8f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TransitPalette.PrimaryBlue,
                                checkedTrackColor = TransitPalette.PrimaryBlue.copy(alpha = 0.3f)
                            )
                        )
                    }

                    HorizontalDivider(
                        color = TransitPalette.BorderSubtle.copy(alpha = 0.2f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Warning, null, tint = TransitPalette.ErrorRed, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                translationManager.translate("Delay Alerts"),
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = (13 * fontSizeMultiplier).sp
                            )
                        }
                        Switch(
                            checked = delayAlerts,
                            onCheckedChange = { delayAlerts = it },
                            modifier = Modifier.graphicsLayer(scaleX = 0.8f, scaleY = 0.8f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TransitPalette.PrimaryBlue,
                                checkedTrackColor = TransitPalette.PrimaryBlue.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }
    }
}

// ==================== THEME SETTINGS CARD ====================

@Composable
fun ThemeSettingsCard(
    isDark: Boolean,
    fontSizeMultiplier: Float,
    activeTheme: String,
    onThemeChange: (String) -> Unit
) {
    val card = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val text = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)

    Card(
        colors = CardDefaults.cardColors(card),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                translationManager.translate("Appearance"),
                color = TransitPalette.PrimaryBlue,
                fontSize = (14 * fontSizeMultiplier).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            listOf("Dark", "Light", "System").forEach { theme ->
                Surface(
                    onClick = { onThemeChange(theme) },
                    color = if (theme == activeTheme) TransitPalette.PrimaryBlue.copy(alpha = 0.1f) else ComposeColor.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            if (theme == activeTheme) 1.dp else 0.dp,
                            TransitPalette.PrimaryBlue,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            val icon = when (theme) {
                                "Dark" -> Icons.Default.DarkMode
                                "Light" -> Icons.Default.LightMode
                                else -> Icons.Default.PhonelinkSetup
                            }
                            Icon(icon, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                translationManager.translate(theme),
                                color = text,
                                fontSize = (13 * fontSizeMultiplier).sp
                            )
                        }
                        if (theme == activeTheme) {
                            Icon(Icons.Default.Check, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

// ==================== LANGUAGE SETTINGS CARD ====================

@Composable
fun LanguageSettingsCard(
    isDark: Boolean,
    fontSizeMultiplier: Float,
    selectedLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit
) {
    val card = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val text = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)

    Card(
        colors = CardDefaults.cardColors(card),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                translationManager.translate("Language"),
                color = TransitPalette.PrimaryBlue,
                fontSize = (14 * fontSizeMultiplier).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                items(AppLanguage.entries.size) { idx ->
                    val lang = AppLanguage.entries[idx]
                    Surface(
                        onClick = { onLanguageChange(lang) },
                        color = if (lang == selectedLanguage) TransitPalette.PrimaryBlue.copy(alpha = 0.1f) else ComposeColor.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                if (lang == selectedLanguage) 1.dp else 0.dp,
                                TransitPalette.PrimaryBlue,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                lang.displayName,
                                color = text,
                                fontSize = (12 * fontSizeMultiplier).sp
                            )
                            if (lang == selectedLanguage) {
                                Icon(Icons.Default.Check, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== FONT SIZE SETTINGS CARD ====================

@Composable
fun FontSizeSettingsCard(
    isDark: Boolean,
    fontSizeMultiplier: Float,
    onFontSizeChange: (Float) -> Unit
) {
    val card = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val text = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)

    Card(
        colors = CardDefaults.cardColors(card),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                translationManager.translate("Font Size"),
                color = TransitPalette.PrimaryBlue,
                fontSize = (14 * fontSizeMultiplier).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("A", color = text, fontSize = 12.sp)
                Slider(
                    value = fontSizeMultiplier,
                    onValueChange = { onFontSizeChange(it) },
                    valueRange = 0.8f..1.5f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = TransitPalette.PrimaryBlue,
                        activeTrackColor = TransitPalette.PrimaryBlue
                    )
                )
                Text("A", color = text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                "${String.format(Locale.US, "%.0f", fontSizeMultiplier * 100)}%",
                color = TransitPalette.PrimaryBlue,
                fontSize = (12 * fontSizeMultiplier).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
            )
        }
    }
}

// ==================== PREFERENCES PANEL ====================

@Composable
fun PreferencesPanel(
    isDark: Boolean,
    fontSizeMultiplier: Float,
    userPrefsManager: UserPreferencesManager
) {
    var showNotifications by remember { mutableStateOf(userPrefsManager.getNotifications()) }
    var enableLocation by remember { mutableStateOf(userPrefsManager.getLocation()) }
    var shareData by remember { mutableStateOf(userPrefsManager.getAnalytics()) }

    LaunchedEffect(showNotifications) {
        userPrefsManager.saveNotifications(showNotifications)
    }

    LaunchedEffect(enableLocation) {
        userPrefsManager.saveLocation(enableLocation)
    }

    LaunchedEffect(shareData) {
        userPrefsManager.saveAnalytics(shareData)
    }

    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)

    Card(
        colors = CardDefaults.cardColors(cardColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                translationManager.translate("Preferences"),
                color = TransitPalette.PrimaryBlue,
                fontSize = (14 * fontSizeMultiplier).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Notifications, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        translationManager.translate("Notifications"),
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = (13 * fontSizeMultiplier).sp
                    )
                }
                Switch(
                    checked = showNotifications,
                    onCheckedChange = { showNotifications = it },
                    modifier = Modifier.graphicsLayer(scaleX = 0.8f, scaleY = 0.8f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TransitPalette.PrimaryBlue,
                        checkedTrackColor = TransitPalette.PrimaryBlue.copy(alpha = 0.3f)
                    )
                )
            }

            HorizontalDivider(
                color = TransitPalette.BorderSubtle.copy(alpha = 0.2f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.LocationOn, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        translationManager.translate("Location Services"),
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = (13 * fontSizeMultiplier).sp
                    )
                }
                Switch(
                    checked = enableLocation,
                    onCheckedChange = { enableLocation = it },
                    modifier = Modifier.graphicsLayer(scaleX = 0.8f, scaleY = 0.8f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TransitPalette.PrimaryBlue,
                        checkedTrackColor = TransitPalette.PrimaryBlue.copy(alpha = 0.3f)
                    )
                )
            }

            HorizontalDivider(
                color = TransitPalette.BorderSubtle.copy(alpha = 0.2f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Security, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        translationManager.translate("Share Analytics"),
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = (13 * fontSizeMultiplier).sp
                    )
                }
                Switch(
                    checked = shareData,
                    onCheckedChange = { shareData = it },
                    modifier = Modifier.graphicsLayer(scaleX = 0.8f, scaleY = 0.8f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TransitPalette.PrimaryBlue,
                        checkedTrackColor = TransitPalette.PrimaryBlue.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

// ==================== ABOUT APP PANEL ====================

@Composable
fun AboutAppPanel(isDark: Boolean, fontSizeMultiplier: Float) {
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)

    Card(
        colors = CardDefaults.cardColors(cardColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "📱 NESO",
                color = TransitPalette.PrimaryBlue,
                fontSize = (20 * fontSizeMultiplier).sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Text(
                translationManager.translate("Your transport companion"),
                color = TransitPalette.TextLow,
                fontSize = (11 * fontSizeMultiplier).sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            HorizontalDivider(
                color = TransitPalette.BorderSubtle.copy(alpha = 0.2f),
                thickness = 0.5.dp,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .padding(vertical = 10.dp)
            )

            Text(
                "Version 1.0.0",
                color = textColor,
                fontSize = (10 * fontSizeMultiplier).sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Build 2026",
                color = TransitPalette.TextLow,
                fontSize = (9 * fontSizeMultiplier).sp
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(TransitPalette.PrimaryBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Privacy",
                        fontSize = (10 * fontSizeMultiplier).sp,
                        color = ComposeColor.White
                    )
                }
                Button(
                    onClick = { },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(TransitPalette.PrimaryBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Terms",
                        fontSize = (10 * fontSizeMultiplier).sp,
                        color = ComposeColor.White
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "© 2026 Neso. All rights reserved.",
                color = TransitPalette.TextLow,
                fontSize = (9 * fontSizeMultiplier).sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==================== LIVE TRACKING CARD ====================

@Composable
fun LiveTrackingCard(
    data: LiveTrackingData,
    isDark: Boolean,
    fontSizeMultiplier: Float
) {
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)

    Card(
        colors = CardDefaults.cardColors(cardColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        translationManager.translate("Currently at"),
                        color = TransitPalette.TextLow,
                        fontSize = (11 * fontSizeMultiplier).sp
                    )
                    Text(
                        data.currentStation,
                        color = textColor,
                        fontSize = (16 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(TransitPalette.PrimaryBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${(data.progress * 100).toInt()}%",
                        color = ComposeColor.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = (12 * fontSizeMultiplier).sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { data.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = TransitPalette.PrimaryBlue,
                trackColor = TransitPalette.InputGrey
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Schedule, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        translationManager.translate("Next Stop"),
                        color = TransitPalette.TextLow,
                        fontSize = (10 * fontSizeMultiplier).sp
                    )
                    Text(
                        data.nextStation,
                        color = textColor,
                        fontSize = (12 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Speed, null, tint = TransitPalette.SuccessGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        translationManager.translate("Speed"),
                        color = TransitPalette.TextLow,
                        fontSize = (10 * fontSizeMultiplier).sp
                    )
                    Text(
                        data.speed,
                        color = textColor,
                        fontSize = (12 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Straighten, null, tint = TransitPalette.TaxiYellow, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Distance",
                        color = TransitPalette.TextLow,
                        fontSize = (10 * fontSizeMultiplier).sp
                    )
                    Text(
                        data.distance,
                        color = textColor,
                        fontSize = (12 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==================== TICKET CARD WITH QR CODE ====================

@Composable
fun TicketCard(
    ticket: BookedJourney,
    isDark: Boolean,
    fontSizeMultiplier: Float
) {
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)
    var showQRCode by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(cardColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        ticket.reference,
                        color = TransitPalette.PrimaryBlue,
                        fontSize = (12 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        ticket.date,
                        color = TransitPalette.TextLow,
                        fontSize = (11 * fontSizeMultiplier).sp
                    )
                }
                Box(
                    modifier = Modifier
                        .background(TransitPalette.SuccessGreen, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        ticket.railcard,
                        color = ComposeColor.White,
                        fontSize = (10 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LocationOn, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size(24.dp))
                    Text(
                        ticket.from,
                        color = textColor,
                        fontSize = (13 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Divider(color = TransitPalette.BorderSubtle, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Icon(Icons.Default.Train, null, tint = TransitPalette.TextLow, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${ticket.duration}min",
                        color = TransitPalette.TextLow,
                        fontSize = (10 * fontSizeMultiplier).sp
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LocationOn, null, tint = TransitPalette.SuccessGreen, modifier = Modifier.size(24.dp))
                    Text(
                        ticket.to,
                        color = textColor,
                        fontSize = (13 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Depart",
                        color = TransitPalette.TextLow,
                        fontSize = (10 * fontSizeMultiplier).sp
                    )
                    Text(
                        ticket.departureTime,
                        color = textColor,
                        fontSize = (14 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(
                        "Arrive",
                        color = TransitPalette.TextLow,
                        fontSize = (10 * fontSizeMultiplier).sp
                    )
                    Text(
                        ticket.arrivalTime,
                        color = textColor,
                        fontSize = (14 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { showQRCode = !showQRCode },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(TransitPalette.PrimaryBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.QrCode, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (showQRCode) "Hide QR Code" else "Show QR Code",
                    color = ComposeColor.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (12 * fontSizeMultiplier).sp
                )
            }

            if (showQRCode) {
                Spacer(Modifier.height(16.dp))
                QRCodeDisplay(ticket.qrCodeContent, isDark)
            }
        }
    }
}

// ==================== QR CODE DISPLAY ====================

@Composable
fun QRCodeDisplay(content: String, isDark: Boolean) {

    val qrCodeBitmap = remember(content) {
        try {
            val writer = MultiFormatWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 300, 300)
            val width = bitMatrix.width
            val height = bitMatrix.height

            val bitmap = android.graphics.Bitmap.createBitmap(
                width,
                height,
                android.graphics.Bitmap.Config.RGB_565
            )

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(
                        x,
                        y,
                        if (bitMatrix[x, y]) android.graphics.Color.BLACK
                        else android.graphics.Color.WHITE
                    )
                }
            }
            bitmap
        } catch (e: Exception) {
            null // 👈 return null instead of crashing
        }
    }

    // 👇 UI handles success/failure
    if (qrCodeBitmap != null) {
        Card(
            colors = CardDefaults.cardColors(ComposeColor.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .border(2.dp, TransitPalette.PrimaryBlue, RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    bitmap = qrCodeBitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.size(200.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Scan for ticket details",
                    color = TransitPalette.TextLow,
                    fontSize = 11.sp
                )
            }
        }
    } else {
        Text(
            "Could not generate QR code",
            color = TransitPalette.ErrorRed
        )
    }
}
// ==================== WALKING ROUTE DETAIL CARD ====================

@Composable
fun WalkingRouteDetailCard(
    route: WalkingRoute,
    isDark: Boolean,
    fontSizeMultiplier: Float
) {
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)

    Card(
        colors = CardDefaults.cardColors(cardColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Route Details",
                        color = textColor,
                        fontSize = (16 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(Icons.Default.CheckCircle, null, tint = TransitPalette.SuccessGreen, modifier = Modifier.size(24.dp))
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TransitPalette.PrimaryBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text("A", color = ComposeColor.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        route.origin?.name ?: "Start",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = (13 * fontSizeMultiplier).sp
                    )
                    Text(
                        route.origin?.address ?: "",
                        color = TransitPalette.TextLow,
                        fontSize = (11 * fontSizeMultiplier).sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(2.dp)
                    .height(20.dp)
                    .background(TransitPalette.BorderSubtle)
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TransitPalette.SuccessGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Text("B", color = ComposeColor.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        route.destination?.name ?: "End",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = (13 * fontSizeMultiplier).sp
                    )
                    Text(
                        route.destination?.address ?: "",
                        color = TransitPalette.TextLow,
                        fontSize = (11 * fontSizeMultiplier).sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            HorizontalDivider(
                color = TransitPalette.BorderSubtle.copy(alpha = 0.2f),
                thickness = 0.5.dp
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Schedule, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${route.durationMinutes}",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = (14 * fontSizeMultiplier).sp
                    )
                    Text(
                        "minutes",
                        color = TransitPalette.TextLow,
                        fontSize = (10 * fontSizeMultiplier).sp
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Straighten, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        String.format(Locale.US, "%.2f", route.distanceMeters / 1000.0),
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = (14 * fontSizeMultiplier).sp
                    )
                    Text(
                        "km",
                        color = TransitPalette.TextLow,
                        fontSize = (10 * fontSizeMultiplier).sp
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Walk",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = (14 * fontSizeMultiplier).sp
                    )
                    Text(
                        "only",
                        color = TransitPalette.TextLow,
                        fontSize = (10 * fontSizeMultiplier).sp
                    )
                }
            }
        }
    }
}

// ==================== SAVED LOCATION CARD ====================

@Composable
fun SavedLocationCard(
    location: SavedLocation,
    isDark: Boolean,
    fontSizeMultiplier: Float,
    onDelete: () -> Unit
) {
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)

    Card(
        colors = CardDefaults.cardColors(cardColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(location.icon, fontSize = (20 * fontSizeMultiplier).sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        location.name,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = (13 * fontSizeMultiplier).sp
                    )
                    Text(
                        location.address,
                        color = TransitPalette.TextLow,
                        fontSize = (11 * fontSizeMultiplier).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = TransitPalette.ErrorRed, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ==================== ROUTE HISTORY CARD ====================

@Composable
fun RouteHistoryCard(
    route: SavedWalkRoute,
    isDark: Boolean,
    fontSizeMultiplier: Float
) {
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.UK)

    Card(
        colors = CardDefaults.cardColors(cardColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(route.origin.icon, fontSize = (16 * fontSizeMultiplier).sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            route.origin.name,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = (12 * fontSizeMultiplier).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.padding(start = (24 * fontSizeMultiplier).sp.value.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ArrowDownward, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size((12 * fontSizeMultiplier).sp.value.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(route.destination.icon, fontSize = (16 * fontSizeMultiplier).sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            route.destination.name,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = (12 * fontSizeMultiplier).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        dateFormat.format(route.timestamp),
                        color = TransitPalette.TextLow,
                        fontSize = (10 * fontSizeMultiplier).sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${route.durationMinutes}m",
                        color = TransitPalette.PrimaryBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = (11 * fontSizeMultiplier).sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${String.format(Locale.US, "%.2f", route.distanceMeters / 1000.0)} km",
                    color = TransitPalette.TextLow,
                    fontSize = (10 * fontSizeMultiplier).sp,
                    modifier = Modifier
                        .background(TransitPalette.InputGrey, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    "Walking",
                    color = TransitPalette.TextLow,
                    fontSize = (10 * fontSizeMultiplier).sp,
                    modifier = Modifier
                        .background(TransitPalette.InputGrey, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ==================== INTERACTIVE MAP VIEW ====================

@Composable
fun InteractiveMapView(isDark: Boolean) {
    AndroidView(
        factory = { context ->
            MapView(context).apply {
                try {
                    setTileSource(TileSourceFactory.MAPNIK)
                    controller.setZoom(16.0)
                    controller.setCenter(GeoPoint(54.0481, -2.8007))
                    setMultiTouchControls(true)
                } catch (e: Exception) {
                    println("Map initialization error: ${e.message}")
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    )
}

// ==================== TRIP ADVISOR SECTION ====================

@Composable
fun TripAdvisorSection(isDark: Boolean, fontSizeMultiplier: Float) {
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                Strings.DISCOVER,
                color = textColor,
                fontSize = (18 * fontSizeMultiplier).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(TransitPalette.TripAdvisorGreen.copy(0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Public, null, tint = TransitPalette.TripAdvisorGreen, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    Strings.TRIP_ADVISOR,
                    color = TransitPalette.TripAdvisorGreen,
                    fontSize = (10 * fontSizeMultiplier).sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        val attractions = listOf(
            Attraction(
                id = 1,
                name = "Lancaster Castle",
                type = "Historical",
                rating = 4.8,
                reviews = 1400,
                priceRange = "££",
                distance = "0.4 mi",
                description = "Medieval castle and museum",
                tags = listOf("History", "Tours")
            ),
            Attraction(
                id = 2,
                name = "Williamson Park",
                type = "Nature",
                rating = 4.7,
                reviews = 920,
                priceRange = "Free",
                distance = "1.2 mi",
                description = "Park with butterfly house and monument",
                tags = listOf("Views", "Park")
            ),
            Attraction(
                id = 3,
                name = "Maritime Museum",
                type = "Culture",
                rating = 4.5,
                reviews = 310,
                priceRange = "£",
                distance = "0.6 mi",
                description = "Local history by the river",
                tags = listOf("Culture", "River")
            ),
            Attraction(
                id = 4,
                name = "Dalton Square",
                type = "Landmark",
                rating = 4.4,
                reviews = 180,
                priceRange = "Free",
                distance = "0.2 mi",
                description = "Historic city square",
                tags = listOf("Statue", "Photo")
            )
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(attractions.size) { index ->
                AttractionCard(attractions[index], isDark, fontSizeMultiplier)
            }
        }
    }
}

// ==================== ATTRACTION CARD ====================

@Composable
fun AttractionCard(
    attraction: Attraction,
    isDark: Boolean,
    fontSizeMultiplier: Float
) {
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)

    Card(
        modifier = Modifier
            .width(180.dp)
            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(cardColor)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(ComposeColor.Gray.copy(0.15f))
            ) {
                Icon(
                    Icons.Default.Image,
                    null,
                    modifier = Modifier.align(Alignment.Center),
                    tint = TransitPalette.TextLow
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(TransitPalette.TaxiYellow, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${attraction.rating}",
                        color = ComposeColor.Black,
                        fontSize = (11.sp) * fontSizeMultiplier,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(Modifier.padding(12.dp)) {
                Text(
                    attraction.name,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = (12 * fontSizeMultiplier).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    attraction.type,
                    color = TransitPalette.TextLow,
                    fontSize = (10 * fontSizeMultiplier).sp
                )

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(5) { i ->
                        Icon(
                            Icons.Default.Star,
                            null,
                            tint = if (i < 4) TransitPalette.TaxiYellow else ComposeColor.Gray.copy(0.3f),
                            modifier = Modifier.size((11 * fontSizeMultiplier).sp.value.dp)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    "${attraction.reviews} reviews",
                    color = TransitPalette.TextLow,
                    fontSize = (9 * fontSizeMultiplier).sp
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        attraction.priceRange,
                        color = TransitPalette.SuccessGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = (10 * fontSizeMultiplier).sp
                    )
                    Text(
                        attraction.distance,
                        color = TransitPalette.TextLow,
                        fontSize = (9 * fontSizeMultiplier).sp
                    )
                }
            }
        }
    }
}

// ==================== QUICK ACTIONS PANEL ====================

@Composable
fun QuickActionsPanel(isDark: Boolean, fontSizeMultiplier: Float) {
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)

    Card(
        colors = CardDefaults.cardColors(cardColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                translationManager.translate("Quick Actions"),
                color = textColor,
                fontSize = (16 * fontSizeMultiplier).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.Search,
                    label = "Search Trains",
                    isDark = isDark,
                    fontSizeMultiplier = fontSizeMultiplier,
                    modifier = Modifier.weight(1f)
                )
                QuickActionButton(
                    icon = Icons.Default.Navigation,
                    label = "Live Map",
                    isDark = isDark,
                    fontSizeMultiplier = fontSizeMultiplier,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.LocationOn,
                    label = "Nearby",
                    isDark = isDark,
                    fontSizeMultiplier = fontSizeMultiplier,
                    modifier = Modifier.weight(1f)
                )
                QuickActionButton(
                    icon = Icons.Default.History,
                    label = "History",
                    isDark = isDark,
                    fontSizeMultiplier = fontSizeMultiplier,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ==================== QUICK ACTION BUTTON ====================

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    isDark: Boolean,
    fontSizeMultiplier: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = { }
) {
    val buttonBg = if (isDark) TransitPalette.InputGrey else ComposeColor(0xFFE8EDF5)
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)

    Button(
        onClick = onClick,
        modifier = modifier
            .height(80.dp)
            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.buttonColors(buttonBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size((24 * fontSizeMultiplier).sp.value.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = (10 * fontSizeMultiplier).sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==================== EMERGENCY PANEL ====================

@Composable
fun EmergencyPanel(isDark: Boolean, fontSizeMultiplier: Float) {
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)

    Card(
        colors = CardDefaults.cardColors(cardColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "🆘 Emergency Contacts",
                color = textColor,
                fontSize = (16 * fontSizeMultiplier).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val emergencyContacts = listOf(
                "🚨 Emergency Services" to "999",
                "🚑 Non-Emergency Medical" to "111",
                "🚔 Police Non-Emergency" to "101",
                "📞 Transport Support" to "03457 484950"
            )

            emergencyContacts.forEach { (name, number) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        name,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = (12 * fontSizeMultiplier).sp,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { },
                        modifier = Modifier
                            .height(32.dp),
                        colors = ButtonDefaults.buttonColors(TransitPalette.ErrorRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Phone, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Call",
                            fontSize = (10 * fontSizeMultiplier).sp,
                            color = ComposeColor.White
                        )
                    }
                }
            }
        }
    }
}

// ==================== FREQUENT DESTINATIONS PANEL ====================

@Composable
fun FrequentDestinationsPanel(isDark: Boolean, fontSizeMultiplier: Float) {
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)

    Card(
        colors = CardDefaults.cardColors(cardColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "⭐ Frequent Destinations",
                color = textColor,
                fontSize = (16 * fontSizeMultiplier).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val destinations = listOf(
                FrequentDestination("🎓 Lancaster University", "🎓", 45, "Today"),
                FrequentDestination("🏪 Shopping Centre", "🏪", 23, "Yesterday"),
                FrequentDestination("🏥 Hospital", "🏥", 12, "3 days ago"),
                FrequentDestination("🍔 City Centre", "🍔", 34, "2 days ago")
            )

            destinations.forEach { dest ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDark) TransitPalette.InputGrey else ComposeColor(0xFFE8EDF5))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            dest.name,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = (13 * fontSizeMultiplier).sp
                        )
                        Text(
                            "${dest.visits} visits • ${dest.lastVisit}",
                            color = TransitPalette.TextLow,
                            fontSize = (10 * fontSizeMultiplier).sp
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        tint = TransitPalette.PrimaryBlue,
                        modifier = Modifier.size((20 * fontSizeMultiplier).sp.value.dp)
                    )
                }
            }
        }
    }
}

// ==================== ANNOUNCEMENTS PANEL ====================

@Composable
fun AnnouncementsPanel(isDark: Boolean, fontSizeMultiplier: Float) {
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White

    Card(
        colors = CardDefaults.cardColors(cardColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "📢 Service Announcements",
                color = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E),
                fontSize = (16 * fontSizeMultiplier).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                colors = CardDefaults.cardColors(ComposeColor(0xFFFFF3CD)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .border(0.5.dp, ComposeColor(0xFFFFD700), RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("⚠️", fontSize = (18 * fontSizeMultiplier).sp, modifier = Modifier.padding(end = 8.dp))
                    Column {
                        Text(
                            "Service Delay",
                            color = ComposeColor(0xFF856404),
                            fontWeight = FontWeight.Bold,
                            fontSize = (12 * fontSizeMultiplier).sp
                        )
                        Text(
                            "Train services delayed by 15 mins",
                            color = ComposeColor(0xFF856404),
                            fontSize = (11 * fontSizeMultiplier).sp
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(ComposeColor(0xFFF8D7DA)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, ComposeColor(0xFFFF6B6B), RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("🚨", fontSize = (18 * fontSizeMultiplier).sp, modifier = Modifier.padding(end = 8.dp))
                    Column {
                        Text(
                            "Maintenance Alert",
                            color = ComposeColor(0xFF721C24),
                            fontWeight = FontWeight.Bold,
                            fontSize = (12 * fontSizeMultiplier).sp
                        )
                        Text(
                            "Track maintenance on Line 2",
                            color = ComposeColor(0xFF721C24),
                            fontSize = (11 * fontSizeMultiplier).sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(TransitPalette.PrimaryBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "View All",
                    color = ComposeColor.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (11 * fontSizeMultiplier).sp
                )
            }
        }
    }
}

// ==================== DATE PICKER MODAL ====================

@Composable
fun DatePickerModal(
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
    isDark: Boolean,
    fontSizeMultiplier: Float
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Select Travel Date",
                    color = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E),
                    fontSize = (18 * fontSizeMultiplier).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    "March 2026",
                    color = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E),
                    fontSize = (14 * fontSizeMultiplier).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val daysInMonth = 31
                Column {
                    repeat((daysInMonth + 6) / 7) { week ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(7) { day ->
                                val dayOfMonth = week * 7 + day - 5
                                if (dayOfMonth in 1..31) {
                                    Button(
                                        onClick = {
                                            val selectedCalendar = java.util.Calendar.getInstance()
                                            selectedCalendar.set(2026, 2, dayOfMonth)
                                            onDateSelected(selectedCalendar.timeInMillis)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            if (dayOfMonth == 28) TransitPalette.PrimaryBlue else TransitPalette.InputGrey
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            "$dayOfMonth",
                                            color = ComposeColor.White,
                                            fontWeight = if (dayOfMonth == 28) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(TransitPalette.PrimaryBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Done", color = ComposeColor.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==================== JOURNEY RESULT CARD ====================

@Composable
fun JourneyResultCard(
    journey: Journey,
    isDark: Boolean,
    fontSizeMultiplier: Float,
    onSelect: (Journey) -> Unit
) {
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)

    Card(
        colors = CardDefaults.cardColors(cardColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable { onSelect(journey) }
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        journey.reference,
                        color = TransitPalette.PrimaryBlue,
                        fontSize = (12 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        journey.date,
                        color = TransitPalette.TextLow,
                        fontSize = (10 * fontSizeMultiplier).sp
                    )
                }
                Icon(Icons.Default.Star, null, tint = TransitPalette.TextLow, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    journey.origin,
                    color = textColor,
                    fontSize = (13 * fontSizeMultiplier).sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(Icons.Default.ArrowForward, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size(18.dp))
                Text(
                    journey.destination,
                    color = textColor,
                    fontSize = (13 * fontSizeMultiplier).sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "${journey.totalDuration} mins • ${journey.railcard}",
                color = TransitPalette.TextLow,
                fontSize = (11 * fontSizeMultiplier).sp
            )
        }
    }
}

// ==================== JOURNEY DETAILS SCREEN ====================

@Composable
fun JourneyDetailsScreen(
    journey: Journey,
    onBack: () -> Unit,
    isDark: Boolean,
    fontSizeMultiplier: Float,
    onBooking: (BookedJourney) -> Unit
) {
    val cardColor = if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White
    val textColor = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) TransitPalette.DeepNavy else ComposeColor(0xFFF2F5F9))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        null,
                        tint = textColor,
                        modifier = Modifier
                            .clickable { onBack() }
                            .size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Journey Details",
                        color = textColor,
                        fontSize = (24 * fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(cardColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    journey.origin,
                                    color = textColor,
                                    fontSize = (18 * fontSizeMultiplier).sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    journey.reference,
                                    color = TransitPalette.TextLow,
                                    fontSize = (11 * fontSizeMultiplier).sp
                                )
                            }
                            Icon(Icons.Default.ArrowForward, null, tint = TransitPalette.PrimaryBlue, modifier = Modifier.size(24.dp))
                            Column {
                                Text(
                                    journey.destination,
                                    color = textColor,
                                    fontSize = (18 * fontSizeMultiplier).sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    journey.date,
                                    color = TransitPalette.TextLow,
                                    fontSize = (11 * fontSizeMultiplier).sp
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        Text(
                            "Journey Legs",
                            color = TransitPalette.PrimaryBlue,
                            fontSize = (14 * fontSizeMultiplier).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        journey.legs.forEach { leg ->
                            Card(
                                colors = CardDefaults.cardColors(TransitPalette.InputGrey),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            leg.transport,
                                            color = ComposeColor.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (12 * fontSizeMultiplier).sp
                                        )
                                        Text(
                                            "${leg.duration}min",
                                            color = TransitPalette.TextLow,
                                            fontSize = (10 * fontSizeMultiplier).sp
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "${leg.origin} → ${leg.destination}",
                                        color = ComposeColor.White,
                                        fontSize = (11 * fontSizeMultiplier).sp
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Depart: ${leg.departureTime} | Arrive: ${leg.arrivalTime}",
                                        color = TransitPalette.TextLow,
                                        fontSize = (10 * fontSizeMultiplier).sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        Button(
                            onClick = {
                                val bookedJourney = BookedJourney(
                                    id = "${System.currentTimeMillis()}",
                                    reference = journey.reference,
                                    from = journey.origin,
                                    to = journey.destination,
                                    date = journey.date,
                                    departureTime = journey.legs[0].departureTime,
                                    arrivalTime = journey.legs[journey.legs.size - 1].arrivalTime,
                                    duration = journey.totalDuration,
                                    railcard = journey.railcard,
                                    qrCodeContent = "NESO-${journey.reference}"
                                )
                                onBooking(bookedJourney)
                                onBack()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(TransitPalette.SuccessGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Book This Journey",
                                color = ComposeColor.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = (14 * fontSizeMultiplier).sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== DRAGGABLE SMS BUTTON ====================

@Composable
fun DraggableSmsButton(
    currentJourney: Journey?,
    isDark: Boolean,
    fontSizeMultiplier: Float
) {
    if (currentJourney == null) return

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showSmsDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
    ) {
        Button(
            onClick = { showSmsDialog = true },
            modifier = Modifier
                .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                .size(56.dp)
                .clip(CircleShape)
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(TransitPalette.SuccessGreen),
            shape = CircleShape
        ) {
            Icon(Icons.Default.Mail, null, tint = ComposeColor.White, modifier = Modifier.size(24.dp))
        }

        if (showSmsDialog) {
            SmsDialog(
                journey = currentJourney,
                isDark = isDark,
                fontSizeMultiplier = fontSizeMultiplier,
                onDismiss = { showSmsDialog = false },
                onSend = { phoneNumber, message ->
                    AppLogger.log("SMS", "Sending SMS to $phoneNumber: $message")
                    showSmsDialog = false
                }
            )
        }
    }
}

// ==================== SMS DIALOG ====================

@Composable
fun SmsDialog(
    journey: Journey,
    isDark: Boolean,
    fontSizeMultiplier: Float,
    onDismiss: () -> Unit,
    onSend: (String, String) -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var message by remember { mutableStateOf(
        "I'm travelling from ${journey.origin} to ${journey.destination} on ${journey.date}. Reference: ${journey.reference}"
    ) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(if (isDark) TransitPalette.SurfaceBlue else ComposeColor.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .border(0.5.dp, TransitPalette.BorderSubtle.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Send Journey via SMS",
                    color = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E),
                    fontSize = (18 * fontSizeMultiplier).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    "Phone Number",
                    color = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E),
                    fontSize = (12 * fontSizeMultiplier).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                TextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    placeholder = { Text("+44 1234567890") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )

                Text(
                    "Message",
                    color = if (isDark) ComposeColor.White else ComposeColor(0xFF1A1C1E),
                    fontSize = (12 * fontSizeMultiplier).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                TextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .padding(bottom = 16.dp),
                    maxLines = 5
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(TransitPalette.InputGrey),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel", color = ComposeColor.White)
                    }

                    Button(
                        onClick = {
                            onSend(phoneNumber, message)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(TransitPalette.SuccessGreen),
                        shape = RoundedCornerShape(8.dp),
                        enabled = phoneNumber.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Mail, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Send SMS", color = ComposeColor.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==================== HELPER FUNCTIONS ====================

fun generateLiveTrackingData(): LiveTrackingData {
    val stations = listOf(
        "Lancaster Station",
        "Carnforth",
        "Oxenholme Lake District",
        "Kendal",
        "Penrith North Lakes",
        "Carlisle"
    )

    val nextStations = listOf(
        "Carnforth",
        "Oxenholme Lake District",
        "Kendal",
        "Penrith North Lakes",
        "Carlisle",
        "Glasgow Central"
    )

    val randomIndex = (Math.random() * stations.size).toInt()
    val progress = (Math.random() * 0.3f + 0.3f).toFloat()
    val speed = (100 + (Math.random() * 50)).toInt()
    val distance = (100 - (progress * 100)).toInt()

    return LiveTrackingData(
        currentStation = stations[randomIndex],
        operator = "TransPennine Express",
        nextStation = nextStations[randomIndex],
        destination = "Glasgow Central",
        upcomingStops = listOf(
            TrainStop(nextStations[randomIndex], "15:42"),
            TrainStop(nextStations[(randomIndex + 1) % nextStations.size], "16:01"),
            TrainStop("Glasgow Central", "16:45")
        ),
        progress = progress,
        distance = "$distance km",
        latitude = 54.0481 + (Math.random() * 0.5),
        longitude = -2.8007 + (Math.random() * 0.5),
        speed = "$speed km/h"
    )
}

fun calculateJourneyRoute(from: SavedLocation, to: SavedLocation): WalkingRoute {
    val lat1 = Math.toRadians(from.latitude)
    val lon1 = Math.toRadians(from.longitude)
    val lat2 = Math.toRadians(to.latitude)
    val lon2 = Math.toRadians(to.longitude)

    val dLat = lat2 - lat1
    val dLon = lon2 - lon1

    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    val distanceMeters = (6371000 * c).toInt()

    val durationMinutes = (distanceMeters / 1.4 / 60).toInt().coerceAtLeast(5)

    val coordinates = mutableListOf<Pair<Double, Double>>()
    coordinates.add(Pair(from.latitude, from.longitude))

    for (i in 1..20) {
        val fraction = i / 20.0
        val lat = from.latitude + (to.latitude - from.latitude) * fraction
        val lon = from.longitude + (to.longitude - from.longitude) * fraction
        coordinates.add(Pair(lat, lon))
    }

    return WalkingRoute(
        origin = from,
        destination = to,
        distanceMeters = distanceMeters,
        durationMinutes = durationMinutes,
        coordinates = coordinates
    )
}

fun getNearbyStations(userLocation: SavedLocation, radius: Double = 50.0): List<TrainStation> {
    val allStations = listOf(
        TrainStation(1, "Lancaster Station", "LAN", "Lancaster", "0.4 km", "15:28", 4, 54.0481, -2.8007),
        TrainStation(2, "Preston Station", "PRE", "Preston", "25.1 km", "15:45", 8, 53.7429, -2.2406),
        TrainStation(3, "Blackpool North", "BPN", "Blackpool", "52.8 km", "16:02", 3, 53.8149, -3.0585),
        TrainStation(4, "Manchester Piccadilly", "MAN", "Manchester", "60.5 km", "16:10", 14, 53.4808, -2.2426),
        TrainStation(5, "Liverpool Lime Street", "LIV", "Liverpool", "75.2 km", "16:45", 7, 53.4069, -2.9789),
        TrainStation(6, "Carlisle", "CAR", "Carlisle", "85.3 km", "17:00", 6, 54.8934, -2.9410)
    )

    return allStations.sortedBy { station ->
        val latDiff = station.latitude - userLocation.latitude
        val lonDiff = station.longitude - userLocation.longitude
        sqrt(latDiff * latDiff + lonDiff * lonDiff)
    }.take(5)
}

fun getRecommendedJourneys(origin: SavedLocation, destinations: List<SavedLocation>): List<Journey> {
    return destinations.take(3).map { destination ->
        Journey(
            origin = origin.name,
            destination = destination.name,
            reference = "TR-${(1000..9999).random()}",
            date = SimpleDateFormat("d MMM yyyy", Locale.UK).format(System.currentTimeMillis() + 86400000),
            totalDuration = (45..180).random(),
            railcard = "Student",
            legs = listOf(
                RouteLeg(
                    transport = "Train",
                    origin = origin.name,
                    destination = destination.name,
                    duration = (45..180).random(),
                    departureTime = "${(9..18).random()}:${listOf("00", "15", "30", "45").random()}",
                    arrivalTime = "${(9..20).random()}:${listOf("00", "15", "30", "45").random()}",
                    platform = "${(1..12).random()}"
                )
            )
        )
    }
}

object TimeFormatter {
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.UK)
    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.UK)
    private val dateTimeFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.UK)

    fun formatTime(timeMillis: Long): String = timeFormat.format(timeMillis)
    fun formatDate(dateMillis: Long): String = dateFormat.format(dateMillis)
    fun formatDateTime(dateTimeMillis: Long): String = dateTimeFormat.format(dateTimeMillis)

    fun getDaysSince(pastMillis: Long): Int {
        val diff = System.currentTimeMillis() - pastMillis
        return (diff / (1000 * 60 * 60 * 24)).toInt()
    }

    fun getTimeUntil(futureMillis: Long): String {
        val diff = futureMillis - System.currentTimeMillis()
        val minutes = diff / (1000 * 60)
        return when {
            minutes < 1 -> "Now"
            minutes < 60 -> "$minutes min"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
    }
}

object DistanceUtils {
    fun metersToKm(meters: Int): String {
        return if (meters >= 1000) {
            String.format(Locale.US, "%.1f km", meters / 1000.0)
        } else {
            "$meters m"
        }
    }

    fun formatSpeed(kmh: Int): String = "$kmh km/h"

    fun estimateTravelTime(distanceMeters: Int, speedKmh: Int = 80): Int {
        val distanceKm = distanceMeters / 1000.0
        return (distanceKm / speedKmh * 60).toInt().coerceAtLeast(1)
    }
}

sealed class AppNotification {
    data class JourneyUpdate(val message: String, val reference: String) : AppNotification()
    data class Announcement(val title: String, val message: String, val severity: String) : AppNotification()
    data class StationAlert(val station: String, val alert: String) : AppNotification()
    data class TicketReminder(val reference: String, val hoursUntilDeparture: Int) : AppNotification()
}

sealed class AppError {
    data class NetworkError(val message: String) : AppError()
    data class AuthenticationError(val message: String) : AppError()
    data class ValidationError(val field: String, val message: String) : AppError()
    data class ServerError(val code: Int, val message: String) : AppError()
    data class UnknownError(val message: String) : AppError()
}

sealed class AppSuccess {
    data class LoginSuccess(val username: String) : AppSuccess()
    data class BookingSuccess(val reference: String, val message: String) : AppSuccess()
    data class UpdateSuccess(val message: String) : AppSuccess()
}

class MockApiClient {
    fun validateLogin(username: String, password: String): Result<Boolean> {
        return if (username.isNotEmpty() && password.isNotEmpty()) {
            Result.success(true)
        } else {
            Result.failure(Exception("Invalid credentials"))
        }
    }

    fun bookTicket(journey: Journey): Result<String> {
        return Result.success(journey.reference)
    }

    fun getStationInfo(stationCode: String): Result<TrainStation?> {
        val station = getNearbyStations(locationLibrary.locations[0])
            .find { it.code == stationCode }
        return if (station != null) {
            Result.success(station)
        } else {
            Result.failure(Exception("Station not found"))
        }
    }

    fun trackTrain(reference: String): Result<LiveTrackingData> {
        return Result.success(generateLiveTrackingData())
    }
}

val mockApiClient = MockApiClient()

object AccessibilityFeatures {
    val highContrast = mutableStateOf(false)
    val enableAnimations = mutableStateOf(true)
    val largeText = mutableStateOf(false)
    val screenReaderEnabled = mutableStateOf(false)

    fun enableHighContrast(enable: Boolean) {
        highContrast.value = enable
    }

    fun enableAnimations(enable: Boolean) {
        enableAnimations.value = enable
    }

    fun enableLargeText(enable: Boolean) {
        largeText.value = enable
    }

    fun enableScreenReader(enable: Boolean) {
        screenReaderEnabled.value = enable
    }
}

object AnimationSpecs {
    val standardDuration = 300
    val shortDuration = 150
    val longDuration = 500

    val standardEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val emphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

object AppLogger {
    fun log(tag: String, message: String) {
        println("[$tag] $message")
    }

    fun logError(tag: String, message: String, exception: Throwable? = null) {
        println("[$tag] ERROR: $message")
        exception?.printStackTrace()
    }

    fun logWarning(tag: String, message: String) {
        println("[$tag] WARNING: $message")
    }
}

object SystemUtils {
    fun getSystemBrightness(context: Context): Int {
        return try {
            android.provider.Settings.System.getInt(
                context.contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                128
            )
        } catch (e: Exception) {
            128
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork
            network != null
        } catch (e: Exception) {
            false
        }
    }

    fun getDeviceName(): String {
        return android.os.Build.MODEL
    }

    fun getAndroidVersion(): Int {
        return android.os.Build.VERSION.SDK_INT
    }
}

// ==================== END OF MAINACTIVITY.KT ====================