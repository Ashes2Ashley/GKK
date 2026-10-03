/* GKK optional signaling server — Cloudflare Worker.
 * The web app works serverless via manual SDP copy/paste (rtcoffer/rtcanswer/rtcaccept).
 * Deploy this Worker and point the app at it with:  sigset wss://<worker>/sig
 * then:  sigsend <room> <json>   to exchange SDP/ICE without copy/paste.
 * Protocol: client opens WebSocket to /sig?room=<room>; every text frame is
 * broadcast to the other sockets in the same room. Use a random room per link.
 */
const rooms = new Map(); // room -> Set<WebSocket>

export default {
  async fetch(req) {
    const url = new URL(req.url);
    if (url.pathname !== "/sig") return new Response("GKK signaling — connect via WebSocket to /sig?room=<room>", { status: 200 });
    const room = url.searchParams.get("room");
    if (!room || !req.headers.get("Upgrade")?.toLowerCase().includes("websocket"))
      return new Response("need ?room= and websocket upgrade", { status: 400 });
    const pair = new WebSocketPair();
    const [client, server] = [pair[0], pair[1]];
    server.accept();
    if (!rooms.has(room)) rooms.set(room, new Set());
    rooms.get(room).add(server);
    server.addEventListener("message", (ev) => {
      for (const peer of rooms.get(room) || []) {
        if (peer !== server) { try { peer.send(ev.data); } catch (e) {} }
      }
    });
    const drop = () => {
      const set = rooms.get(room);
      if (set) { set.delete(server); if (!set.size) rooms.delete(room); }
    };
    server.addEventListener("close", drop);
    server.addEventListener("error", drop);
    return new Response(null, { status: 101, webSocket: client });
  },
};
