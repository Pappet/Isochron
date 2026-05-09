## 2025-02-23 - [Critical SSRF in Network Discovery]
**Vulnerability:** The `NetworkDiscovery` UPnP scanner blindly fetched the XML from any URL returned in the `LOCATION` header of an SSDP response.
**Learning:** This is a Server-Side Request Forgery (SSRF). Any malicious device on the local network could have returned a response causing the app to send a GET request to an arbitrary URL (like the router admin page or loopback addresses).
**Prevention:** Always validate URLs parsed from untrusted local network responses. Specifically for UPnP, ensure the `URL.host` strictly matches the IP address of the UDP packet sender and that the protocol is HTTP or HTTPS.
