import re

with open('/home/peter/Projekte/ScannerApp/app/src/main/java/com/scanner/app/util/PortScanner.kt', 'r') as f:
    content = f.read()

# Replace quickConnect
quick_old = """    suspend fun quickConnect(ip: String, port: Int, timeoutMs: Int): Pair<Int, Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(
                    InetSocketAddress(ip, port),
                    timeoutMs
                )
                try { socket.close() } catch (_: Exception) {}
                Pair(port, true)
            } catch (e: Exception) {
                Pair(port, false)
            }
        }"""
        
quick_new = """    suspend fun quickConnect(ip: String, port: Int, timeoutMs: Int): Pair<Int, Boolean> =
        withContext(Dispatchers.IO) {
            var socket: java.net.Socket? = null
            try {
                socket = java.net.Socket()
                socket.connect(
                    java.net.InetSocketAddress(ip, port),
                    timeoutMs
                )
                Pair(port, true)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Pair(port, false)
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }"""

content = content.replace(quick_old, quick_new)

# Replace scanPortFull
scan_old = """    private suspend fun scanPortFull(
        ip: String,
        port: Int,
        connectTimeoutMs: Int = 1500,
        grabBanner: Boolean = true
    ): PortScanResult = withContext(Dispatchers.IO) {
        val wellKnownName = WellKnownPorts.serviceName(port)
        val bannerTimeoutMs = 2000

        try {
            val socket = Socket()
            val startTime = System.nanoTime()

            socket.connect(
                InetSocketAddress(ip, port),
                connectTimeoutMs
            )

            val latency = (System.nanoTime() - startTime) / 1_000_000f

            var banner: String? = null
            var detectedProtocol = DetectedProtocol.UNKNOWN

            if (grabBanner) {
                try {
                    socket.soTimeout = bannerTimeoutMs

                    // Step 1: Try passive read first (SSH, FTP, SMTP send greetings)
                    val inputStream = socket.getInputStream()
                    val outputStream = socket.getOutputStream()

                    val passiveBanner = try {
                        socket.soTimeout = 1500
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                        if (inputStream.available() > 0 || waitForData(inputStream, 1200)) {
                            reader.readLine()?.take(300)
                        } else null
                    } catch (_: Exception) { null }

                    if (passiveBanner != null) {
                        banner = passiveBanner
                        detectedProtocol = detectProtocolFromBanner(passiveBanner)
                    }

                    // Step 2: If no passive banner, send HTTP probe
                    if (banner == null || detectedProtocol == DetectedProtocol.UNKNOWN) {
                        try {
                            val httpProbe = "HEAD / HTTP/1.0\\r\\nHost: $ip\\r\\nConnection: close\\r\\n\\r\\n"
                            outputStream.write(httpProbe.toByteArray())
                            outputStream.flush()
                            
                            val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                            val response = java.lang.StringBuilder()
                            var linesRead = 0
                            
                            while (linesRead < 10) {
                                val line = reader.readLine() ?: break
                                response.appendLine(line)
                                linesRead++
                                // Stop after headers
                                if (line.isBlank()) break
                            }
                            
                            if (response.isNotEmpty()) {
                                banner = response.toString().trim()
                                detectedProtocol = detectProtocolFromBanner(banner)
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {
                    detectedProtocol = detectFromPortHint(port)
                }
            }

            try { socket.close() } catch (_: Exception) {}

            PortScanResult(
                ip = ip,
                port = port,
                state = PortState.OPEN,
                serviceName = wellKnownName,
                banner = banner,
                detectedProtocol = detectedProtocol,
                latencyMs = latency
            )

        } catch (_: java.net.ConnectException) {
            PortScanResult(ip = ip, port = port, state = PortState.CLOSED, serviceName = wellKnownName)
        } catch (_: java.net.SocketTimeoutException) {
            PortScanResult(ip = ip, port = port, state = PortState.FILTERED, serviceName = wellKnownName)
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning $ip:$port", e)
            PortScanResult(ip = ip, port = port, state = PortState.CLOSED, serviceName = wellKnownName)
        }
    }"""
    
# Use regex to do a more flexible replacement because of imports/whitespace
import re

scan_pattern = re.compile(r'private suspend fun scanPortFull\([\s\S]*?PortScanResult\(ip = ip, port = port, state = PortState\.CLOSED, serviceName = wellKnownName\)\s*\n\s*}', re.MULTILINE)

scan_new = """    private suspend fun scanPortFull(
        ip: String,
        port: Int,
        connectTimeoutMs: Int = 1500,
        grabBanner: Boolean = true
    ): PortScanResult = withContext(Dispatchers.IO) {
        val wellKnownName = WellKnownPorts.serviceName(port)
        val bannerTimeoutMs = 2000
        var socket: java.net.Socket? = null

        try {
            socket = java.net.Socket()
            val startTime = System.nanoTime()

            socket.connect(
                java.net.InetSocketAddress(ip, port),
                connectTimeoutMs
            )

            val latency = (System.nanoTime() - startTime) / 1_000_000f

            var banner: String? = null
            var detectedProtocol = DetectedProtocol.UNKNOWN

            if (grabBanner) {
                try {
                    socket.soTimeout = bannerTimeoutMs

                    val inputStream = socket.getInputStream()
                    val outputStream = socket.getOutputStream()

                    val passiveBanner = try {
                        socket.soTimeout = 1500
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                        if (inputStream.available() > 0 || waitForData(inputStream, 1200)) {
                            reader.readLine()?.take(300)
                        } else null
                    } catch (_: Exception) { null }

                    if (passiveBanner != null) {
                        banner = passiveBanner
                        detectedProtocol = detectProtocolFromBanner(passiveBanner)
                    }

                    if (banner == null || detectedProtocol == DetectedProtocol.UNKNOWN) {
                        try {
                            val httpProbe = "HEAD / HTTP/1.0\\r\\nHost: $ip\\r\\nConnection: close\\r\\n\\r\\n"
                            outputStream.write(httpProbe.toByteArray())
                            outputStream.flush()
                            
                            val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                            val response = java.lang.StringBuilder()
                            var linesRead = 0
                            
                            while (linesRead < 10) {
                                val line = reader.readLine() ?: break
                                response.appendLine(line)
                                linesRead++
                                if (line.isBlank()) break
                            }
                            
                            if (response.isNotEmpty()) {
                                banner = response.toString().trim()
                                detectedProtocol = detectProtocolFromBanner(banner)
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {
                    detectedProtocol = detectFromPortHint(port)
                }
            }

            PortScanResult(
                ip = ip,
                port = port,
                state = PortState.OPEN,
                serviceName = wellKnownName,
                banner = banner,
                detectedProtocol = detectedProtocol,
                latencyMs = latency
            )

        } catch (_: java.net.ConnectException) {
            PortScanResult(ip = ip, port = port, state = PortState.CLOSED, serviceName = wellKnownName)
        } catch (_: java.net.SocketTimeoutException) {
            PortScanResult(ip = ip, port = port, state = PortState.FILTERED, serviceName = wellKnownName)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.w(TAG, "Error scanning $ip:$port", e)
            PortScanResult(ip = ip, port = port, state = PortState.CLOSED, serviceName = wellKnownName)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }"""

content = scan_pattern.sub(scan_new, content)

with open('/home/peter/Projekte/ScannerApp/app/src/main/java/com/scanner/app/util/PortScanner.kt', 'w') as f:
    f.write(content)
