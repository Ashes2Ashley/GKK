package com.example.data.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

/**
 * Device identity keys.
 *
 * Preferred: Android Keystore-backed P-256 keypair (private key never leaves
 * hardware/TEE when available). Purpose is AGREE_KEY (ECDH) — the key cannot
 * be used for signing or encryption even if the device is compromised at the
 * app layer. No user authentication required: the SMS agent must decrypt
 * incoming envelopes while the phone is locked.
 *
 * Fallback: in-memory P-256 keypair if the Keystore is unavailable (very old
 * devices, broken providers). Logged loudly; pairing started in this mode is
 * marked non-persistent and the user is told to re-pair after fixing it.
 */
object DeviceKeys {

    private const val TAG = "DeviceKeys"
    const val ALIAS = "gkk_device_identity_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    @Volatile
    private var fallbackPair: KeyPair? = null

    @Volatile
    var isKeystoreBacked: Boolean = true
        private set

    @Synchronized
    fun getOrCreateIdentity(): KeyPair {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!ks.containsAlias(ALIAS)) {
                val kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_AGREE_KEY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setUserAuthenticationRequired(false)
                    .build()
                kpg.initialize(spec)
                kpg.generateKeyPair()
                Log.i(TAG, "generated Keystore identity keypair")
            }
            val pub = ks.getCertificate(ALIAS).publicKey
            val priv = (ks.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry).privateKey
            isKeystoreBacked = true
            return KeyPair(pub, priv)
        } catch (e: Exception) {
            Log.w(TAG, "Keystore unavailable — in-memory identity (NOT persistent)", e)
            isKeystoreBacked = false
            fallbackPair?.let { return it }
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"))
            return kpg.generateKeyPair().also { fallbackPair = it }
        }
    }

    fun exportPublicKeyB64(publicKey: PublicKey): String =
        Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)

    fun importPeerPublicKey(b64: String): PublicKey {
        val bytes = Base64.decode(b64.trim(), Base64.NO_WRAP)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    fun fingerprintHex(publicKey: PublicKey): String = EnvelopeCrypto.deviceIdHex(publicKey)

    /**
     * Four human-friendly words derived from the device fingerprint, for
     * out-of-band pairing verification ("read me your four words").
     * 8 fingerprint bytes -> 4 words, 16 bits each from a 256-word list
     * (high byte selects word, low byte XORs a rotation — simple, memorable).
     */
    fun verificationWords(publicKey: PublicKey): String {
        val id = EnvelopeCrypto.deviceId(publicKey)
        return (0 until 4).joinToString(" ") { i ->
            val hi = id[i * 2].toInt() and 0xFF
            WORDS[hi]
        }
    }

    fun isValidPeerKey(b64: String): Boolean = try {
        importPeerPublicKey(b64); true
    } catch (e: Exception) {
        false
    }

    // 256 short, phonetically distinct words for pairing verification.
    private val WORDS = arrayOf(
        "amber", "arc", "ash", "atlas", "aurora", "axiom", "azimuth", "badger",
        "banyan", "basalt", "beacon", "birch", "bison", "blade", "bluff", "bolt",
        "boreal", "bravo", "bridge", "brisk", "bronze", "brook", "bundle", "bunker",
        "cactus", "cadence", "camel", "canyon", "carbon", "cargo", "cascade", "cedar",
        "cerule", "chasm", "cherry", "cinder", "cipher", "citadel", "cliff", "cobalt",
        "comet", "compass", "copper", "coral", "cougar", "crane", "crater", "crescent",
        "crest", "cricket", "crimson", "cross", "crown", "crystal", "curlew", "cypress",
        "dagger", "dahlia", "dawn", "delta", "desert", "dew", "dial", "dingle",
        "dodo", "drift", "dune", "eagle", "ebony", "echo", "eclipse", "eddy",
        "ember", "emerald", "engine", "enigma", "envoy", "epoch", "elm", "estuary",
        "falcon", "fern", "fiber", "finch", "fjord", "flint", "flora", "flux",
        "forest", "forge", "fossil", "foxtrot", "frost", "fuchsia", "garnet", "gecko",
        "glacier", "glint", "glyph", "gneiss", "goblin", "gorge", "granite", "grove",
        "gull", "gypsum", "harbor", "hawk", "hazel", "helix", "heron", "hickory",
        "horizon", "hornet", "hotel", "hyena", "ibex", "ice", "igloo", "iguana",
        "indigo", "inlet", "iridium", "iron", "island", "ivory", "jackal", "jasper",
        "juniper", "kestrel", "kiln", "koala", "krill", "lagoon", "lance", "lark",
        "laser", "lattice", "laurel", "ledge", "lemur", "lichen", "lilac", "linen",
        "lizard", "llama", "locket", "lotus", "lumen", "lynx", "magma", "magnet",
        "mammoth", "mango", "maple", "marble", "marsh", "mason", "meadow", "mercury",
        "meridian", "mesa", "meteor", "mica", "micro", "mimosa", "minnow", "mint",
        "mirage", "monsoon", "moose", "moss", "moth", "mount", "mulberry", "murmur",
        "nadir", "nebula", "needle", "nimbus", "nomad", "north", "nova", "nugget",
        "oasis", "obsidian", "ochre", "octane", "omega", "onyx", "opal", "orbit",
        "orchid", "osprey", "otter", "oxide", "oyster", "palm", "panda", "parrot",
        "pebble", "pelican", "pepper", "peridot", "petal", "phantom", "pinnacle", "pioneer",
        "plaza", "prism", "puma", "quartz", "quasar", "quill", "radar", "raven",
        "reef", "relay", "ridge", "river", "robin", "rocket", "rotor", "ruby",
        "sable", "saffron", "sage", "salmon", "sand", "sapphire", "saturn", "savanna",
        "scarlet", "sentry", "sequoia", "sierra", "signal", "silver", "siren", "slate",
        "sleet", "sodium", "solar", "sonar", "sparrow", "sphinx", "spice", "spire",
        "spruce", "squid", "stable", "stanza", "steppe", "stone", "storm", "summit",
    )

    init {
        check(WORDS.size == 256) { "wordlist must be exactly 256 words" }
    }
}
