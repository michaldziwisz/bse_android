package eu.blueseaeye.bse.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Utrzymuje ruch aplikacji przypięty do sieci Wi-Fi urządzenia BlueSeaEye
 * (SoftAP bez internetu), tak aby system NIE przełączył nas w trakcie odczytu
 * ze steru na inną zapamiętaną sieć (np. statkowe Wi-Fi z internetem, które
 * chwilowo ma lepszy zasięg).
 *
 * Mechanizm (Android 10+/API 29):
 *  1. [WifiNetworkSpecifier] opisuje konkretną sieć (SSID + hasło WPA2).
 *  2. [ConnectivityManager.requestNetwork] prosi system o połączenie z NIĄ —
 *     przy pierwszym użyciu system pokazuje jednorazowe okno „Połączyć z
 *     BlueSeaEye?”, które użytkownik zatwierdza.
 *  3. Po uzyskaniu sieci [ConnectivityManager.bindProcessToNetwork] kieruje
 *     CAŁY ruch tego procesu (zapytania HTTP do 192.168.4.1) właśnie tam.
 *
 * Dzięki temu telefon może dalej mieć internet na swojej sieci głównej, a my i
 * tak trzymamy się urządzenia. Gdy odczyt jest wyłączany, wiązanie jest
 * zdejmowane ([stop]) i telefon wraca do normalnego zarządzania siecią.
 */
class DeviceNetworkBinder(context: Context) {

    /** Stan wiązania do sieci urządzenia — do pokazania/ogłoszenia w UI. */
    enum class Status { INACTIVE, CONNECTING, BOUND, UNAVAILABLE }

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var callback: ConnectivityManager.NetworkCallback? = null

    private val _status = MutableStateFlow(Status.INACTIVE)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Wiązanie do konkretnej sieci działa dopiero od Androida 10 (API 29). */
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Rozpoczyna trzymanie ruchu aplikacji przy sieci [ssid]. Bezpieczne do
     * wielokrotnego wołania — jeśli wiązanie już trwa, nic nie robi. Musi być
     * wołane, gdy aplikacja jest na pierwszym planie (systemowe okno zgody).
     */
    @SuppressLint("NewApi")
    fun start(ssid: String, passphrase: String) {
        if (!isSupported()) return
        if (callback != null) return
        _status.value = Status.CONNECTING

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(passphrase)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            // Sieć urządzenia to lokalny access point BEZ internetu — nie żądamy
            // tej zdolności, inaczej system nie dopasuje sieci.
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Przypnij cały ruch procesu do sieci urządzenia.
                connectivityManager.bindProcessToNetwork(network)
                _status.value = Status.BOUND
            }

            override fun onLost(network: Network) {
                connectivityManager.bindProcessToNetwork(null)
                _status.value = Status.CONNECTING
            }

            override fun onUnavailable() {
                _status.value = Status.UNAVAILABLE
            }
        }

        callback = cb
        try {
            connectivityManager.requestNetwork(request, cb)
        } catch (_: Exception) {
            callback = null
            _status.value = Status.UNAVAILABLE
        }
    }

    /** Zdejmuje wiązanie i przywraca normalne zarządzanie siecią przez system. */
    fun stop() {
        val cb = callback
        try {
            connectivityManager.bindProcessToNetwork(null)
            if (cb != null) connectivityManager.unregisterNetworkCallback(cb)
        } catch (_: Exception) {
            // Callback mógł już nie być zarejestrowany — ignorujemy.
        }
        callback = null
        _status.value = Status.INACTIVE
    }
}
