package f3l1x.catter.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object Connectivity {

    fun isOnline(context: Context): Boolean = manager(context)?.online() ?: false

    fun observe(context: Context): Flow<Boolean> = callbackFlow {
        val manager = manager(context)
        if (manager == null) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(manager.online())
            }

            override fun onLost(network: Network) {
                trySend(manager.online())
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.usable())
            }
        }

        manager.registerDefaultNetworkCallback(callback)
        trySend(manager.online())
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun manager(context: Context): ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private fun ConnectivityManager.online(): Boolean {
        val network = activeNetwork ?: return false
        return getNetworkCapabilities(network)?.usable() ?: false
    }

    private fun NetworkCapabilities.usable(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            (
                hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                )
}
