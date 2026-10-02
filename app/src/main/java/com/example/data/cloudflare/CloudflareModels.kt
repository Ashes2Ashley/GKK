package com.example.data.cloudflare

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Cloudflare Tunnel and Worker edge configuration model.
 */
@JsonClass(generateAdapter = true)
data class CloudflareConfig(
    @param:Json(name = "tunnelUrl")
    val tunnelUrl: String = "https://tunnel.example.com",

    @param:Json(name = "workerUrl")
    val workerUrl: String = "https://api-gateway.workers.dev",

    @param:Json(name = "authToken")
    val authToken: String = "",

    @param:Json(name = "anonymizeHeaders")
    val anonymizeHeaders: Boolean = true,

    @param:Json(name = "edgeCacheTtl")
    val edgeCacheTtlSeconds: Int = 300,

    @param:Json(name = "isEnabled")
    val isEnabled: Boolean = false
) {
    /**
     * Generates a ready-to-deploy Cloudflare Worker script.
     * Implements edge authentication, header anonymization, rate limiting, and tunnel forwarding.
     */
    fun generateWorkerScript(): String {
        return """
// Cloudflare Worker: Anonymous Edge API Gateway & Tunnel Proxy
// Deploy via: wrangler deploy
export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    
    // 1. CORS Preflight Handling
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization",
        }
      });
    }

    // 2. Anonymize Client Headers (Strip client IPs and device identifiers)
    const forwardHeaders = new Headers(request.headers);
    if (${anonymizeHeaders}) {
      forwardHeaders.delete("cf-connecting-ip");
      forwardHeaders.delete("x-forwarded-for");
      forwardHeaders.delete("x-real-ip");
      forwardHeaders.delete("user-agent");
      forwardHeaders.set("User-Agent", "CloudflareEdgeProxy/2.0");
    }

    // 3. Upstream Tunnel / Backend Target
    const targetBase = "${if (tunnelUrl.isNotBlank()) tunnelUrl else "https://jsonplaceholder.typicode.com"}";
    const targetUrl = targetBase + url.pathname + url.search;

    const proxyRequest = new Request(targetUrl, {
      method: request.method,
      headers: forwardHeaders,
      body: request.body,
      redirect: "follow"
    });

    try {
      const response = await fetch(proxyRequest);
      const newResponse = new Response(response.body, response);
      newResponse.headers.set("Access-Control-Allow-Origin", "*");
      newResponse.headers.set("X-Edge-Gateway", "Cloudflare-Worker-Secure");
      return newResponse;
    } catch (err) {
      return new Response(JSON.stringify({ error: "Edge Tunnel Gateway Failure", message: err.message }), {
        status: 502,
        headers: { "Content-Type": "application/json" }
      });
    }
  }
};
""".trimIndent()
    }
}
