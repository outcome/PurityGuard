package com.purityguard.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.net.VpnService
import android.provider.Settings
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class PurityVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var blockPageServer: LocalBlockPageServer? = null
    private var vpnThread: Thread? = null
    private val running = AtomicBoolean(false)
    private val stopInProgress = AtomicBoolean(false)
    @Volatile private var explicitStopRequested = false

    @Volatile private var lastRedirectAtMs = 0L
    @Volatile private var rapidBlockWindowStartMs = 0L
    @Volatile private var rapidBlockCount = 0

    private val domainNotificationLastSentMs = ConcurrentHashMap<String, Long>()
    @Volatile private var globalNotifWindowStartMs = 0L
    @Volatile private var globalNotifCount = 0

    override fun onCreate() {
        super.onCreate()
        Log.i("PurityGuard", "PurityVpnService onCreate")
        DebugLogStore.add("SERVICE", "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action")
        Log.i("PurityGuard", "SERVICE_CMD action=$action")
        DebugLogStore.add("SERVICE_CMD", "action=$action")
        when (action) {
            ACTION_STOP -> {
                explicitStopRequested = true
                stopVpn("ACTION_STOP")
            }
            ACTION_DISABLE_PROTECTION -> {
                Log.w("PurityGuard", "Direct disable action blocked; routing must occur via MainActivity flow")
            }
            ACTION_DEBUG_SIMULATE_BLOCK -> {
                val domain = intent.getStringExtra(EXTRA_DEBUG_DOMAIN).orEmpty().ifBlank { "debug.blocked.test" }
                Log.i("PurityGuard", "DEBUG_SIMULATE_BLOCK domain=$domain")
                onBlockedDomainEvent(domain, SettingsStore(this))
            }
            else -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        try {
            explicitStopRequested = false
            if (!running.compareAndSet(false, true)) return
            stopInProgress.set(false)

            ensureNotificationChannels()
            startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification())

            val privateDnsMode = readPrivateDnsMode()
            val systemDnsServers = getSystemDnsServers()
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) cm?.activeNetwork else null
            
            val settingsStore = SettingsStore(this)
            val provider = settingsStore.getDnsProvider()
            val captureTargets = DnsCapturePlanner.buildCaptureTargets(systemDnsServers, BuildConfig.DEBUG, provider)
            val forwardingTargets = DnsCapturePlanner.buildForwardingTargets(systemDnsServers, BuildConfig.DEBUG, provider)

            Log.i("PurityGuard", "DNS_CAPTURE_SETUP provider=${provider.value} privateDnsMode=$privateDnsMode systemDns=${systemDnsServers.joinToString { it.hostAddress ?: "?" }} captureTargets=${captureTargets.joinToString { it.hostAddress ?: "?" }} forwardingTargets=${forwardingTargets.joinToString { it.hostAddress ?: "?" }} debug=${BuildConfig.DEBUG}")

            val builder = Builder()
                .setSession("PurityGuard")
                .setMtu(1280)
                .addAddress("10.8.0.2", 32)
                .addAddress("fd00::1", 128)
                .addRoute("::", 0) // IPv6 Blackhole route: Intercept all IPv6 and drop it
                .addDisallowedApplication(packageName)

            captureTargets.forEach { dns ->
                builder.addDnsServer(dns)
                builder.addRoute(dns, DnsCapturePlanner.routePrefixLength(dns))
            }

            Log.i("PurityGuard", "INTERCEPTING_DNS: ${captureTargets.joinToString { it.hostAddress ?: "?" }}")
            Log.i("PurityGuard", "FORWARDING_DNS_UPSTREAMS: ${forwardingTargets.joinToString { it.hostAddress ?: "?" }}")

            builder.allowBypass()
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e("PurityGuard", "VpnService.Builder.establish() returned null")
                DebugLogStore.add("ERROR", "establish_returned_null")
                running.set(false)
                stopSelf()
                return
            }

            val blocker = DomainBlocker(BlocklistRepository(this).loadMergedList())
            runStartupBlockSanityCheck(blocker)

            blockPageServer = LocalBlockPageServer(
                context = this,
                inspirationModeProvider = { settingsStore.getInspirationMode() },
                donationUrlProvider = { settingsStore.getDonationUrl() }
            ).also { it.start() }

            val input = FileInputStream(vpnInterface!!.fileDescriptor)
            val output = FileOutputStream(vpnInterface!!.fileDescriptor)

            vpnThread = thread(name = "vpn-loop") {
                val packet = ByteArray(32767)
                while (running.get()) {
                    val len = try {
                        input.read(packet)
                    } catch (_: Exception) {
                        if (running.get()) Log.w("PurityGuard", "VPN input read failed during active run")
                        -1
                    }

                    if (len <= 0) continue

                    val version = (packet[0].toInt() ushr 4) and 0x0F

                    val parsed = PacketParsers.parseDnsQuery(packet, len)
                    if (parsed == null) {
                        // 1. IPv6 Blackhole: Drop all NON-DNS IPv6 traffic for stability.
                        // This forces the OS to use IPv4 for data where our bypass is 100% reliable.
                        if (version == 6) continue

                        // Unknown/parse-failure traffic should never be blocked.
                        Log.i("PurityGuard", parseMissAllowLogLine(len))
                        continue
                    }

                    val domain = parsed.domain
                    val domainLabel = domain ?: "<unparsed>"

                    Log.d("PurityGuard", "DNS query received domain=$domainLabel")

                    if (domain != null && domain.equals("use-application-dns.net", ignoreCase = true)) {
                        Log.i("PurityGuard", "CANARY_INTERCEPT domain=$domain decision=NXDOMAIN")
                        val nx = DnsResponseBuilder.buildNxdomain(parsed.rawDnsPayload)
                        val responseIpPacket = PacketParsers.buildUdpIpResponse(packet, len, nx)
                        try {
                            output.write(responseIpPacket)
                            output.flush()
                        } catch (_: Exception) {}
                        continue
                    }

                    if (domain != null && blocker.shouldBlock(domain)) {
                        val isDohBootstrap = blocker.isDohBootstrapDomain(domain)
                        val reason = if (isDohBootstrap) "doh_bootstrap" else "blocklist_match"
                        Log.i("PurityGuard", "DNS_DECISION domain=$domain decision=BLOCK reason=$reason type=${parsed.queryType}")
                        val blocked = DnsResponseBuilder.buildBlockedResponse(parsed.rawDnsPayload)
                        val responseIpPacket = PacketParsers.buildUdpIpResponse(packet, len, blocked)
                        try {
                            output.write(responseIpPacket)
                            output.flush()
                            Log.d("PurityGuard", "DNS response write success domain=$domain type=blocked")
                        } catch (e: Exception) {
                            Log.e("PurityGuard", "DNS response write failed domain=$domain type=blocked", e)
                            if (!running.get()) break
                        }
                        if (!isDohBootstrap) {
                            onBlockedDomainEvent(domain, settingsStore)
                        } else {
                            Log.i("PurityGuard", "DOH_BOOTSTRAP_BLOCKED_SILENT domain=$domain")
                        }
                    } else {
                        // Fail-open: if the domain is unparseable, we still forward upstream.
                        Log.i("PurityGuard", "DNS_DECISION domain=$domainLabel decision=ALLOW reason=not_blocklisted")
                        val response = queryUpstreamDns(forwardingTargets, domainLabel, parsed.rawDnsPayload)
                        if (response != null) {
                            val responseIpPacket = PacketParsers.buildUdpIpResponse(packet, len, response)
                            try {
                                output.write(responseIpPacket)
                                output.flush()
                                Log.d("PurityGuard", "DNS response write success domain=$domainLabel type=upstream")
                            } catch (e: Exception) {
                                Log.e("PurityGuard", "DNS response write failed domain=$domainLabel type=upstream", e)
                                if (!running.get()) break
                            }
                        } else {
                            Log.w("PurityGuard", "DNS forward failed all upstream targets domain=$domainLabel; returning SERVFAIL")
                            val servfail = DnsResponseBuilder.buildServfailResponse(parsed.rawDnsPayload)
                            val responseIpPacket = PacketParsers.buildUdpIpResponse(packet, len, servfail)
                            try {
                                output.write(responseIpPacket)
                                output.flush()
                                Log.d("PurityGuard", "DNS response write success domain=$domainLabel type=servfail")
                            } catch (e: Exception) {
                                Log.e("PurityGuard", "DNS response write failed domain=$domainLabel type=servfail", e)
                                if (!running.get()) break
                            }
                        }
                    }
                }

                try { input.close() } catch (_: Exception) {}
                try { output.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("PurityGuard", "startVpn failed", e)
            DebugLogStore.add("ERROR", "startVpn failed: ${e.message}")
            running.set(false)
            stopSelf()
        }
    }

    private fun onBlockedDomainEvent(domain: String, settingsStore: SettingsStore) {
        ensureNotificationChannels()

        val now = System.currentTimeMillis()
        settingsStore.recordBlockedAlert(domain, now)
        DebugLogStore.add("BLOCK_EVENT", "domain=$domain now=$now")

        if (false && settingsStore.isRapidBlockGuardEnabled() && tripRapidBlockGuard(now)) {
            settingsStore.setProtectionEnabled(false)
            showProtectionDisabledWarning("Unusually high block rate detected. Protection auto-disabled.")
            Log.e("PurityGuard", "Rapid block guard tripped; auto-disabled protection")
            DebugLogStore.add("GUARD", "rapid_block_guard_tripped")
            stopVpn("rapid_block_guard")
            return
        }

        val globalShouldNotify = shouldSendNotification(domain, now)

        val openBlockedIntent = Intent(this, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(BlockedActivity.EXTRA_DOMAIN, domain)
            action = "com.purityguard.app.OPEN_BLOCKED_${now}"
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            (now % Int.MAX_VALUE).toInt(),
            openBlockedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        var notificationActuallySent = false
        if (globalShouldNotify) {
            sendBlockedNotification(pendingIntent, now, domain, "standard")
            notificationActuallySent = true
        }

        if (!BuildConfig.DEBUG) {
            Log.i("PurityGuard", "REDIRECT_SKIPPED release_build domain=$domain")
            return
        }

        val redirectEnabled = BuildConfig.DEBUG && settingsStore.isRedirectEnabled()
        val redirectFallbackEnabled = BuildConfig.DEBUG && settingsStore.isRedirectFallbackEnabled()
        val redirectOrder = settingsStore.getRedirectOrder()
        val plan = BlockEventPolicy.plan(
            nowMs = now,
            lastRedirectAtMs = lastRedirectAtMs,
            cooldownMs = SettingsStore.BLOCKED_EVENT_COOLDOWN_MS,
            redirectEnabled = redirectEnabled,
            redirectOrder = redirectOrder,
            notificationSent = notificationActuallySent
        )

        Log.i(
            "PurityGuard",
            "REDIRECT_PLAN domain=$domain redirectEnabled=$redirectEnabled fallbackEnabled=$redirectFallbackEnabled notificationSent=$notificationActuallySent order=${redirectOrder.joinToString(",") { it.value }} methods=${plan.methodsToAttemptInOrder.joinToString(",") { it.value }} preemptive=${plan.shouldSendPreemptiveFallback} disabledFallback=${plan.shouldSendDisabledFallback} cooldown=${plan.cooldownActive}"
        )

        if (plan.cooldownActive) {
            DebugLogStore.add("REDIRECT", "cooldown active domain=$domain")
            Log.i("PurityGuard", "AUTO_OPEN_COOLDOWN_ACTIVE domain=$domain")
            return
        }
        lastRedirectAtMs = now

        if (plan.shouldSendPreemptiveFallback && globalShouldNotify && !notificationActuallySent) {
            sendBlockedNotification(pendingIntent, now, domain, "redirect_preemptive_fallback")
            notificationActuallySent = true
        }

        var redirectSuccess = false
        for (method in plan.methodsToAttemptInOrder) {
            val ok = tryRedirectMethod(method, domain, settingsStore)
            DebugLogStore.add("REDIRECT", "method=${method.value} domain=$domain success=$ok")
            if (ok) {
                Log.i("PurityGuard", "AUTO_OPEN_FIRED domain=$domain method=${method.value}")
                redirectSuccess = true
                Log.i("PurityGuard", "REDIRECT_CHAIN_RESULT domain=$domain success=true method=${method.value}")
                break
            }
            if (!redirectFallbackEnabled) {
                DebugLogStore.add("REDIRECT", "fallback disabled; stopping chain")
                break
            }
        }

        if (!redirectSuccess && plan.shouldSendDisabledFallback && globalShouldNotify && !notificationActuallySent) {
            sendBlockedNotification(pendingIntent, now, domain, "redirect_disabled_fallback")
            notificationActuallySent = true
        }

        if (!redirectSuccess && globalShouldNotify && !notificationActuallySent) {
            sendBlockedNotification(pendingIntent, now, domain, "redirect_failure_fallback")
            notificationActuallySent = true
        }

        if (!redirectSuccess) {
            Log.i("PurityGuard", "REDIRECT_CHAIN_RESULT domain=$domain success=false")
        }
    }

    private fun tryRedirectMethod(method: RedirectMethod, domain: String, settingsStore: SettingsStore): Boolean {
        return try {
            when (method) {
                RedirectMethod.ACTIVITY -> {
                    val intent = Intent(this, BlockedActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(BlockedActivity.EXTRA_DOMAIN, domain)
                    }
                    startActivity(intent)
                    true
                }

                RedirectMethod.LOCALHOST_PAGE -> {
                    val url = "http://127.0.0.1/?domain=" + URLEncoder.encode(domain, StandardCharsets.UTF_8.name())
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    true
                }

                RedirectMethod.EXTERNAL_URL -> {
                    val template = settingsStore.getExternalRedirectUrlTemplate().ifBlank {
                        SettingsStore.DEFAULT_EXTERNAL_REDIRECT_URL_TEMPLATE
                    }
                    val encoded = URLEncoder.encode(domain, StandardCharsets.UTF_8.name())
                    val url = template.replace("{domain}", encoded)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    true
                }
            }
        } catch (e: SecurityException) {
            Log.w("PurityGuard", "Redirect suppressed method=${method.value} domain=$domain reason=security_exception", e)
            DebugLogStore.add("REDIRECT", "method=${method.value} domain=$domain suppressed=security_exception")
            false
        } catch (e: Exception) {
            Log.w("PurityGuard", "Redirect failed method=${method.value} domain=$domain reason=exception", e)
            false
        }
    }

    private fun sendBlockedNotification(pendingIntent: PendingIntent, now: Long, domain: String, reason: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Use a per-event ID to avoid stale notification content being reused.
        // Notification auto-expires after 10s, so tray spam remains controlled.
        val notifId = (now % Int.MAX_VALUE).toInt()

        val builder = Notification.Builder(this, ALERT_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Blocked Unsafe Content")
            .setContentText("")
            .setSubText("")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setCategory(Notification.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setTimeoutAfter(10000) // DISAPPEAR after 10 seconds (Banner only)
            .setContentIntent(pendingIntent)
            .setVisibility(Notification.VISIBILITY_SECRET) // Hide content from lockscreen
            .addAction(buildDisableProtectionAction())

        try {
            mgr.notify(notifId.toInt(), builder.build())
            Log.i("PurityGuard", "BLOCK_NOTIFICATION_SENT domain=$domain id=$notifId reason=$reason")
            DebugLogStore.add("NOTIFY", "domain=$domain id=$notifId reason=$reason sent=true")
        } catch (e: SecurityException) {
            Log.w("PurityGuard", "BLOCK_NOTIFICATION_SKIPPED domain=$domain missing_permission", e)
        } catch (e: Exception) {
            Log.w("PurityGuard", "BLOCK_NOTIFICATION_FAILED domain=$domain", e)
        }
    }

    private fun runStartupBlockSanityCheck(blocker: DomainBlocker) {
        val checks = listOf("google.com", "youtube.com", "pornhub.com", "img.phncdn.com")
        checks.forEach { domain ->
            val decision = if (blocker.shouldBlock(domain)) "BLOCK" else "ALLOW"
            Log.i("PurityGuard", "STARTUP_SANITY domain=$domain decision=$decision")
        }
    }

    private fun shouldSendNotification(domain: String, nowMs: Long): Boolean {
        // PER-DOMAIN COOLDOWN (60s to prevent spam on retries)
        val normalized = domain.lowercase().trim()
        val lastForDomain = domainNotificationLastSentMs[normalized] ?: 0L
        if (nowMs - lastForDomain < 60_000L) return false

        // GLOBAL COOLDOWN (5s to prevent notification "pile up")
        if (nowMs - globalNotifWindowStartMs < 5_000L) return false

        // Update timestamps
        domainNotificationLastSentMs[normalized] = nowMs
        globalNotifWindowStartMs = nowMs
        return true
    }

    private fun tripRapidBlockGuard(nowMs: Long): Boolean {
        if (rapidBlockWindowStartMs == 0L || nowMs - rapidBlockWindowStartMs > RAPID_BLOCK_WINDOW_MS) {
            rapidBlockWindowStartMs = nowMs
            rapidBlockCount = 1
            return false
        }
        rapidBlockCount += 1
        return rapidBlockCount >= RAPID_BLOCK_THRESHOLD
    }

    private fun showProtectionDisabledWarning(reason: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openMainIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            this,
            991,
            openMainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = Notification.Builder(this, ALERT_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("PurityGuard paused for safety")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        mgr.notify(992, n)
    }

    private fun buildDisableProtectionAction(): Notification.Action {
        val disableIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_REQUEST_DISABLE_FLOW, true)
        }
        val pending = PendingIntent.getActivity(
            this,
            993,
            disableIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Action.Builder(
            null,
            "Disable Protection",
            pending
        ).build()
    }

    private fun queryUpstreamDns(targets: List<InetAddress>, domain: String, queryPayload: ByteArray): ByteArray? {
        for (target in targets) {
            val response = querySingleUpstream(target, domain, queryPayload)
            if (response != null) return response
        }
        return null
    }

    private fun readPrivateDnsMode(): String {
        return try {
            val mode = Settings.Global.getString(contentResolver, "private_dns_mode") ?: "unknown"
            val specifier = Settings.Global.getString(contentResolver, "private_dns_specifier")
            if (!specifier.isNullOrBlank()) "$mode($specifier)" else mode
        } catch (e: Exception) {
            Log.w("PurityGuard", "Unable to read private DNS mode", e)
            "unavailable"
        }
    }

    private fun getSystemDnsServers(): List<InetAddress> {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return emptyList()
        val linkProperties = cm.getLinkProperties(cm.activeNetwork) ?: return emptyList()
        return linkProperties.dnsServers?.toList() ?: emptyList()
    }

    private fun querySingleUpstream(upstreamDns: InetAddress, domain: String, queryPayload: ByteArray): ByteArray? {
        val socket = try {
            DatagramSocket().also { protect(it) }
        } catch (e: Exception) {
            Log.e("PurityGuard", "DNS socket create/protect failed domain=$domain upstream=${upstreamDns.hostAddress}", e)
            return null
        }

        return try {
            socket.soTimeout = 1500
            socket.send(DatagramPacket(queryPayload, queryPayload.size, upstreamDns, 53))
            val buf = ByteArray(1500)
            val p = DatagramPacket(buf, buf.size)
            socket.receive(p)
            Log.d("PurityGuard", "DNS forward success domain=$domain upstream=${upstreamDns.hostAddress} bytes=${p.length} version=${if (upstreamDns is java.net.Inet6Address) 6 else 4}")
            buf.copyOf(p.length)
        } catch (e: Exception) {
            Log.w("PurityGuard", "DNS forward timeout/failure domain=$domain upstream=${upstreamDns.hostAddress} error=${e.message}")
            null
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    @Synchronized
    private fun stopVpn(reason: String) {
        if (!stopInProgress.compareAndSet(false, true)) return
        Log.i("PurityGuard", "Stopping VPN: $reason")

        running.set(false)

        try { blockPageServer?.stop() } catch (_: Exception) {}
        blockPageServer = null

        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null

        val t = vpnThread
        vpnThread = null
        if (t != null && t.isAlive && t !== Thread.currentThread()) {
            try { t.join(400) } catch (_: Exception) {}
        }

        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        try { stopSelf() } catch (e: Exception) { Log.e("PurityGuard", "stopSelf failed during stopVpn reason=$reason", e) }
    }

    override fun onDestroy() {
        stopVpn("onDestroy")
        maybeScheduleRestart("onDestroy")
        super.onDestroy()
    }

    override fun onRevoke() {
        Log.w("PurityGuard", "VPN_REVOKED")
        DebugLogStore.add("SERVICE", "onRevoke")
        stopVpn("onRevoke")
        maybeScheduleRestart("onRevoke")
        super.onRevoke()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        maybeScheduleRestart("onTaskRemoved")
    }

    private fun maybeScheduleRestart(reason: String) {
        if (explicitStopRequested) return
        val settings = SettingsStore(this)
        if (!settings.isProtectionEnabled()) return

        val prepIntent = VpnService.prepare(this)
        if (prepIntent != null) {
            Log.w("PurityGuard", "SKIP_AUTO_RESTART reason=$reason vpn_permission_missing")
            DebugLogStore.add("SERVICE", "skip_restart reason=$reason vpn_permission_missing")
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val i = Intent(this, PurityVpnService::class.java).putExtra("action", "start")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(i)
                } else {
                    startService(i)
                }
                Log.i("PurityGuard", "AUTO_RESTART_SCHEDULED reason=$reason")
                DebugLogStore.add("SERVICE", "auto_restart reason=$reason")
            } catch (e: Exception) {
                Log.e("PurityGuard", "AUTO_RESTART_FAILED reason=$reason", e)
                DebugLogStore.add("ERROR", "auto_restart_failed reason=$reason ${e.message}")
            }
        }, 1200)
    }

    private fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        mgr.createNotificationChannel(
            NotificationChannel(
                SERVICE_NOTIFICATION_CHANNEL_ID,
                "Purity Guard Service",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        mgr.createNotificationChannel(
            NotificationChannel(
                ALERT_NOTIFICATION_CHANNEL_ID,
                "Purity Guard Block Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when domains are blocked"
                enableVibration(true)
                setShowBadge(true)
            }
        )
    }

    private fun buildServiceNotification(): Notification {
        val providerLabel = SettingsStore(this).getDnsProvider().label
        return Notification.Builder(this, SERVICE_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Purity Guard active")
            .setContentText("Filtering through $providerLabel")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .addAction(buildDisableProtectionAction())
            .build()
    }

    companion object {
        @JvmStatic
        internal fun parseMissAllowLogLine(packetLen: Int): String {
            return "DNS_ALLOW_PATH domain=<unknown> reason=parse_miss len=$packetLen decision=ALLOW"
        }

        private const val SERVICE_NOTIFICATION_CHANNEL_ID = "purity_guard_service"
        private const val ALERT_NOTIFICATION_CHANNEL_ID = "purity_guard_alerts"
        private const val SERVICE_NOTIFICATION_ID = 101

        private const val ACTION_STOP = "stop"
        private const val ACTION_DISABLE_PROTECTION = "disable_protection"
        const val ACTION_DEBUG_SIMULATE_BLOCK = "debug_simulate_block"
        const val EXTRA_DEBUG_DOMAIN = "debug_domain"

        private const val RAPID_BLOCK_WINDOW_MS = 15_000L
        private const val RAPID_BLOCK_THRESHOLD = 25

        private const val DOMAIN_NOTIFICATION_COOLDOWN_MS = 10_000L
        private const val GLOBAL_NOTIFICATION_WINDOW_MS = 60_000L
        private const val GLOBAL_NOTIFICATION_MAX_PER_WINDOW = 3
    }
}




