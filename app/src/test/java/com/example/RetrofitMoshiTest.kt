package com.example

import com.example.data.api.RetrofitClient
import com.example.data.api.adapter.IsoDateAdapter
import com.example.data.api.interceptor.CurlGenerator
import com.example.data.api.interceptor.CustomHeaderInterceptor
import com.example.data.api.model.Post
import com.example.data.api.model.User
import com.example.data.api.service.ApiService
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RetrofitMoshiTest {

    @Test
    fun `test retrofit instance and api service creation`() {
        val service: ApiService = RetrofitClient.apiService
        assertNotNull("ApiService instance should not be null", service)

        val dynamicService = RetrofitClient.create<ApiService>()
        assertNotNull("Reified create service should produce valid instance", dynamicService)
    }

    @Test
    fun `test moshi serialization of Post`() {
        val post = Post(
            id = 101,
            userId = 2,
            title = "Testing Retrofit and Moshi",
            body = "Ensuring seamless JSON communication"
        )

        val json = RetrofitClient.postToJson(post)
        assertNotNull(json)
        assertTrue(json.contains("\"id\": 101"))
        assertTrue(json.contains("\"userId\": 2"))
        assertTrue(json.contains("\"title\": \"Testing Retrofit and Moshi\""))
    }

    @Test
    fun `test moshi deserialization of Post`() {
        val rawJson = """
            {
                "id": 42,
                "userId": 5,
                "title": "Clean Architecture with Retrofit",
                "body": "Fast, safe, and modern network operations in Android."
            }
        """.trimIndent()

        val parsed = RetrofitClient.postFromJson(rawJson)
        assertNotNull(parsed)
        assertEquals(42, parsed?.id)
        assertEquals(5, parsed?.userId)
        assertEquals("Clean Architecture with Retrofit", parsed?.title)
        assertEquals("Fast, safe, and modern network operations in Android.", parsed?.body)
    }

    @Test
    fun `test moshi deserialization of nested User model`() {
        val rawUserJson = """
            {
                "id": 1,
                "name": "Ada Lovelace",
                "username": "ada",
                "email": "ada@computing.org",
                "phone": "555-0199",
                "company": {
                    "name": "Analytical Engine Co",
                    "catchPhrase": "First computer programmer"
                }
            }
        """.trimIndent()

        val adapter = RetrofitClient.moshi.adapter(User::class.java)
        val user = adapter.fromJson(rawUserJson)

        assertNotNull(user)
        assertEquals(1, user?.id)
        assertEquals("Ada Lovelace", user?.name)
        assertEquals("Analytical Engine Co", user?.company?.name)
        assertEquals("First computer programmer", user?.company?.catchPhrase)
    }

    @Test
    fun `test custom IsoDateAdapter`() {
        val adapter = IsoDateAdapter()
        val now = Date(1700000000000L) // known timestamp
        val json = adapter.toJson(now)
        assertNotNull(json)
        assertTrue(json!!.contains("2023"))

        val parsed = adapter.fromJson(json)
        assertNotNull(parsed)
        assertEquals(now.time, parsed!!.time)
    }

    @Test
    fun `test curl generator`() {
        val request = Request.Builder()
            .url("https://jsonplaceholder.typicode.com/posts")
            .header("Authorization", "Bearer test_token_123")
            .header("Accept", "application/json")
            .get()
            .build()

        val curl = CurlGenerator.toCurl(request)
        assertTrue(curl.contains("curl -X GET"))
        assertTrue(curl.contains("Authorization: Bearer test_token_123"))
        assertTrue(curl.contains("https://jsonplaceholder.typicode.com/posts"))
    }

    @Test
    fun `test custom header interceptor`() {
        val interceptor = CustomHeaderInterceptor()
        interceptor.bearerToken = "secret_jwt"
        interceptor.setHeader("X-Custom-Client", "Retrofit-App")

        val headers = interceptor.getAllHeaders()
        assertEquals("Retrofit-App", headers["X-Custom-Client"])
        assertEquals("secret_jwt", interceptor.bearerToken)
    }

    @Test
    fun `test cloudflare worker script generation`() {
        val config = com.example.data.cloudflare.CloudflareConfig(
            tunnelUrl = "https://tunnel.mycorp.org",
            workerUrl = "https://proxy.workers.dev",
            anonymizeHeaders = true
        )
        val script = config.generateWorkerScript()
        assertTrue(script.contains("CloudflareEdgeProxy/2.0"))
        assertTrue(script.contains("https://tunnel.mycorp.org"))
        assertTrue(script.contains("cf-connecting-ip"))
    }

    @Test
    fun `test sip packet generation`() {
        val session = com.example.data.telecom.SipSession(
            profile = com.example.data.telecom.SipProfile(
                username = "bob",
                domain = "telecom.net",
                transport = "TLS"
            )
        )
        val packet = session.generateRegisterPacket()
        assertTrue(packet.contains("REGISTER sip:bob@telecom.net SIP/2.0"))
        assertTrue(packet.contains("User-Agent: ApiConnect-SIP-Engine/2.0"))
    }

    @Test
    fun `test sms intent builder`() {
        val intent = com.example.data.telecom.SmsGatewayManager.buildSmsIntent("+15551234", "Verification Code: 9912")
        assertEquals(android.content.Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto:%2B15551234", intent.dataString)
    }
}
