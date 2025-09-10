package com.pharadev.receiptscanner.security

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Singleton
class NetworkSecurityHelper @Inject constructor() {
    
    companion object {
        // Add your API domains and their certificate pins here
        // These are example pins - replace with actual certificate pins for your API
        private val CERTIFICATE_PINS = mapOf<String, String>(
            // Example: "api.yourservice.com" to "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        )
    }
    
    fun createSecureOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .certificatePinner(createCertificatePinner())
            .hostnameVerifier(createHostnameVerifier())
            .build()
    }
    
    private fun createCertificatePinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()
        
        // Add certificate pins for your API endpoints
        // Example implementation - modify based on your actual API endpoints
        CERTIFICATE_PINS.forEach { (domain, pin) ->
            // builder.add(domain, pin)
        }
        
        return builder.build()
    }
    
    private fun createHostnameVerifier(): HostnameVerifier {
        return HostnameVerifier { hostname, session ->
            // Implement custom hostname verification if needed
            // For production, use proper verification
            true
        }
    }
    
    fun createTrustAllManager(): X509TrustManager {
        return object : X509TrustManager {
            @Throws(CertificateException::class)
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                // Trust all client certificates - modify for production use
            }

            @Throws(CertificateException::class)
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                // Trust all server certificates - modify for production use
                // In production, implement proper certificate validation
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf()
            }
        }
    }
    
    fun createSecureSSLContext(): SSLContext? {
        return try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(createTrustAllManager()), null)
            sslContext
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}