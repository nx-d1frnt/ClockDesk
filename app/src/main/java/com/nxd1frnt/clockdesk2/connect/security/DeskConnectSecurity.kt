package com.nxd1frnt.clockdesk2.connect.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.PreferenceManager
import com.nxd1frnt.clockdesk2.utils.Logger
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import java.util.UUID
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class DeskConnectSecurity(context: Context) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    var deviceId: String
        private set

    var keyPair: KeyPair
        private set

    var certificate: X509Certificate
        private set

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        var savedDeviceId = prefs.getString("device_id", null)
        if (savedDeviceId == null || savedDeviceId.length < 32 || savedDeviceId.startsWith("clockdesk_")) {
            savedDeviceId = UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString("device_id", savedDeviceId).apply()
        }
        deviceId = savedDeviceId

        val (loadedKeyPair, loadedCert) = loadOrGenerateKeysAndCert()
        keyPair = loadedKeyPair
        certificate = loadedCert
    }

    private fun loadOrGenerateKeysAndCert(): Pair<KeyPair, X509Certificate> {
        val privKeyBase64 = prefs.getString("private_key", null)
        val pubKeyBase64 = prefs.getString("public_key", null)
        val certBase64 = prefs.getString("certificate", null)

        if (privKeyBase64 != null && pubKeyBase64 != null && certBase64 != null) {
            try {
                val keyFactory = java.security.KeyFactory.getInstance("RSA")
                val privBytes = Base64.decode(privKeyBase64, Base64.DEFAULT)
                val pubBytes = Base64.decode(pubKeyBase64, Base64.DEFAULT)
                val certBytes = Base64.decode(certBase64, Base64.DEFAULT)

                val privKeySpec = java.security.spec.PKCS8EncodedKeySpec(privBytes)
                val pubKeySpec = java.security.spec.X509EncodedKeySpec(pubBytes)

                val privateKey = keyFactory.generatePrivate(privKeySpec)
                val publicKey = keyFactory.generatePublic(pubKeySpec)
                val keyPair = KeyPair(publicKey, privateKey)

                val cf = CertificateFactory.getInstance("X.509")
                val cert = cf.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate

                if (cert.subjectDN.name.contains(deviceId)) {
                    return Pair(keyPair, cert)
                }
            } catch (e: Exception) {
                Logger.e("DeskConnectSecurity") { "Failed to load stored keys/cert: ${e.message}, regenerating..." }
            }
        }

        return generateAndStoreKeysAndCert()
    }

    private fun generateAndStoreKeysAndCert(): Pair<KeyPair, X509Certificate> {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048, SecureRandom())
        val kp = keyGen.generateKeyPair()

        val nameBuilder = X500NameBuilder(BCStyle.INSTANCE)
        nameBuilder.addRDN(BCStyle.CN, deviceId)
        nameBuilder.addRDN(BCStyle.OU, "KDE Connect")
        nameBuilder.addRDN(BCStyle.O, "KDE")

        val notBefore = Date(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000L)
        val notAfter = Date(System.currentTimeMillis() + 10L * 365 * 24 * 60 * 60 * 1000L)

        val certBuilder = JcaX509v3CertificateBuilder(
            nameBuilder.build(),
            BigInteger.ONE,
            notBefore,
            notAfter,
            nameBuilder.build(),
            kp.public
        )

        val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        val certHolder = certBuilder.build(signer)
        val cert = JcaX509CertificateConverter().getCertificate(certHolder)

        prefs.edit()
            .putString("private_key", Base64.encodeToString(kp.private.encoded, Base64.DEFAULT))
            .putString("public_key", Base64.encodeToString(kp.public.encoded, Base64.DEFAULT))
            .putString("certificate", Base64.encodeToString(cert.encoded, Base64.DEFAULT))
            .apply()

        return Pair(kp, cert)
    }

    fun getCertificateFingerprint(cert: X509Certificate = certificate): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(cert.encoded)
        return digest.joinToString(":") { String.format("%02x", it) }
    }

    fun trustDevice(deviceId: String, cert: X509Certificate) {
        val fp = getCertificateFingerprint(cert)
        prefs.edit().putString("trusted_device_$deviceId", fp).apply()
        Logger.d("DeskConnectSecurity") { "Trusted device $deviceId with fingerprint $fp" }
    }

    fun unpairDevice(deviceId: String) {
        prefs.edit().remove("trusted_device_$deviceId").apply()
        Logger.d("DeskConnectSecurity") { "Unpaired device $deviceId" }
    }

    fun isDevicePaired(deviceId: String): Boolean {
        return prefs.contains("trusted_device_$deviceId")
    }

    fun getPairedDeviceIds(): Set<String> {
        return prefs.all.keys
            .filter { it.startsWith("trusted_device_") }
            .map { it.removePrefix("trusted_device_") }
            .toSet()
    }

    fun convertToSslSocket(socket: Socket, clientMode: Boolean, onPeerCertReceived: ((X509Certificate) -> Unit)? = null): SSLSocket {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry(
                "key",
                keyPair.private,
                "".toCharArray(),
                arrayOf(certificate)
            )
        }

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, "".toCharArray())

        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(certs: Array<out X509Certificate>?, authType: String?) {
                if (!certs.isNullOrEmpty()) onPeerCertReceived?.invoke(certs[0])
            }
            override fun checkServerTrusted(certs: Array<out X509Certificate>?, authType: String?) {
                if (!certs.isNullOrEmpty()) onPeerCertReceived?.invoke(certs[0])
            }
        })

        val tlsContext = SSLContext.getInstance("TLSv1.2")
        tlsContext.init(kmf.keyManagers, trustAllCerts, SecureRandom())

        val sslSocket = tlsContext.socketFactory.createSocket(
            socket,
            socket.inetAddress.hostAddress,
            socket.port,
            true
        ) as SSLSocket

        sslSocket.soTimeout = 10000
        if (clientMode) {
            sslSocket.useClientMode = true
        } else {
            sslSocket.useClientMode = false
            sslSocket.wantClientAuth = true
        }

        return sslSocket
    }
}
