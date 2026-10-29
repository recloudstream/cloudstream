package com.lagradost.cloudstream3.syncproviders.local

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean

class LocalShareActivity : AppCompatActivity() {
    private val active = AtomicBoolean(false)
    private var receiver: LocalPairingTransport.Receiver? = null
    private var network: LocalDeviceDiscovery? = null
    private lateinit var status: TextView
    private var permissionAction: (() -> Unit)? = null
    @Volatile private var generation = 0L
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        val action = permissionAction; permissionAction = null
        if (allowed) action?.invoke() else status.setText(R.string.local_share_permission_denied)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding((24*density).toInt(),(24*density).toInt(),(24*density).toInt(),(24*density).toInt()) }
        layout.addView(TextView(this).apply { setText(R.string.local_share_title); textSize = 26f })
        status = TextView(this).apply { setText(R.string.local_share_description); textSize = 16f; setPadding(0,16,0,24) }
        layout.addView(status)
        fun action(title: Int, run: () -> Unit) { layout.addView(Button(this).apply { setText(title); isAllCaps = false; setOnClickListener { withNetworkPermission(run) } }) }
        action(R.string.local_share_receive, ::receive)
        action(R.string.local_share_send_recent) { chooseRecent() }
        if (intent.action == Intent.ACTION_SEND) action(R.string.local_share_send_title) { sendSharedText() }
        layout.addView(Button(this).apply { setText(android.R.string.cancel); setOnClickListener { finish() } })
        setContentView(ScrollView(this).apply { addView(layout) })
    }
    override fun onStart() { super.onStart(); active.set(true) }
    override fun onStop() { active.set(false); stopNetwork(); super.onStop() }
    private fun stopNetwork() { generation++; receiver?.close(); receiver = null; network?.close(); network = null }

    private fun withNetworkPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 37 && applicationInfo.targetSdkVersion >= 37 &&
            ContextCompat.checkSelfPermission(this,"android.permission.ACCESS_LOCAL_NETWORK") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionAction = action; permission.launch("android.permission.ACCESS_LOCAL_NETWORK")
        } else action()
    }

    private fun receive() {
        stopNetwork()
        val currentGeneration = generation
        status.setText(R.string.local_share_starting)
        lifecycleScope.launch {
            var created: LocalPairingTransport.Receiver? = null
            try {
                val session = withContext(Dispatchers.IO) {
                    LocalPairingTransport.Receiver(receive = { bytes ->
                        val transfer = SharedWatchTransfer.decode(bytes)
                        if (!active.get() || generation != currentGeneration || transfer == null) false else {
                            runOnUiThread { if (active.get() && generation == currentGeneration) confirm(transfer) }; true
                        }
                    }, ended = { runOnUiThread {
                        if (generation == currentGeneration) {
                            network?.close(); network = null
                            if (active.get()) status.setText(R.string.local_share_reception_finished)
                        }
                    } }).also { created = it }
                }
                if (!active.get() || generation != currentGeneration) { session.close(); return@launch }
                receiver = session
                val addresses = withContext(Dispatchers.IO) {
                    NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                        .filter { !it.isLoopbackAddress && LocalPairingTransport.isLocal(it) }
                        .joinToString("\n") { address ->
                            val host = address.hostAddress.orEmpty()
                            if (':' in host) "[$host]:${session.port}" else "$host:${session.port}"
                        }
                }
                status.text = getString(R.string.local_share_receiver_code, session.code.chunked(4).joinToString("-"), addresses)
                session.start()
                runCatching { network = LocalDeviceDiscovery(this@LocalShareActivity) { _,_,_ -> }; network?.advertise(session.port) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { stopNetwork(); status.setText(R.string.local_share_failed) }
            finally { if (receiver !== created) created?.close() }
        }
    }

    private fun confirm(transfer: SharedWatchTransfer) {
        AlertDialog.Builder(this).setTitle(transfer.title)
            .setMessage(getString(R.string.local_share_confirm, transfer.apiName, transfer.position / 1000))
            .setPositiveButton(R.string.local_share_open) { _, _ ->
                PendingWatchTransfer.put(transfer)
                val api = Base64.encodeToString(transfer.apiName.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val url = Base64.encodeToString(transfer.url.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                startActivity(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("csshare:$api?$url")))
                finish()
            }.setNeutralButton(R.string.local_share_search) { _,_ ->
                startActivity(Intent(this,MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                    .setData(Uri.parse("cloudstreamsearch://"+Uri.encode(transfer.title))))
                finish()
            }.setNegativeButton(android.R.string.cancel,null).show()
    }

    private fun chooseRecent() {
        lifecycleScope.launch {
            val entries = HomeViewModel.getResumeWatching().orEmpty().take(50)
            if (entries.isEmpty()) { status.setText(R.string.local_share_no_recent); return@launch }
            AlertDialog.Builder(this@LocalShareActivity).setTitle(R.string.local_share_send_recent)
                .setItems(entries.map { it.name }.toTypedArray()) { _, index ->
                    val entry = entries[index]
                    choosePeer(SharedWatchTransfer(title=entry.name, apiName=entry.apiName, url=entry.url,
                        season=entry.season, episode=entry.episode, position=entry.watchPos?.position ?: 0,
                        duration=entry.watchPos?.duration ?: 0))
                }.setNegativeButton(android.R.string.cancel,null).show()
        }
    }

    private fun sendSharedText() {
        lifecycleScope.launch {
            val raw = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (raw.length > 16_384) { status.setText(R.string.local_share_invalid); return@launch }
            val parsedUri = Uri.parse(raw)
            val link = if (parsedUri.isHierarchical) parsedUri.getQueryParameter("redirectto") ?: raw else raw
            val parts = link.removePrefix("csshare:").split("?", limit=2)
            val parsed = runCatching {
                if (!link.startsWith("csshare:") || parts.size != 2) return@runCatching null
                val api = String(Base64.decode(parts[0],Base64.DEFAULT),Charsets.UTF_8)
                val url = String(Base64.decode(parts[1],Base64.DEFAULT),Charsets.UTF_8)
                val recent = HomeViewModel.getResumeWatching().orEmpty().firstOrNull { it.apiName == api && it.url == url }
                SharedWatchTransfer(title=intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() } ?: recent?.name ?: api,
                    apiName=api,url=url,season=recent?.season,episode=recent?.episode,
                    position=recent?.watchPos?.position ?: 0,duration=recent?.watchPos?.duration ?: 0)
            }.getOrNull()
            if (parsed?.isValid() == true) choosePeer(parsed) else status.setText(R.string.local_share_invalid)
        }
    }

    private fun choosePeer(transfer: SharedWatchTransfer) {
        if (!transfer.isValid()) { status.setText(R.string.local_share_invalid); return }
        stopNetwork()
        val peers = linkedMapOf<String, Pair<InetAddress,Int>>()
        val labels = arrayListOf<String>()
        val adapter = ArrayAdapter(this,android.R.layout.simple_list_item_1,labels)
        val list = ListView(this).apply { this.adapter = adapter; minimumHeight = (160*resources.displayMetrics.density).toInt() }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.local_share_find_devices).setView(list)
            .setNeutralButton(R.string.local_share_manual) { _, _ -> manualPeer(transfer) }
            .setNegativeButton(android.R.string.cancel,null).create()
        list.setOnItemClickListener { _,_,index,_ ->
            val peer = peers[labels[index]] ?: return@setOnItemClickListener
            dialog.dismiss(); askCode(transfer,peer.first,peer.second)
        }
        dialog.setOnDismissListener { network?.close(); network=null }
        dialog.show()
        runCatching {
            network = LocalDeviceDiscovery(this) { name,address,port -> runOnUiThread {
                if (active.get() && dialog.isShowing) {
                    if (!peers.containsKey(name)) labels.add(name)
                    peers[name]=address to port; adapter.notifyDataSetChanged()
                }
            } }; network?.discover()
        }
    }

    private fun manualPeer(transfer: SharedWatchTransfer) {
        val input=EditText(this).apply { hint="192.168.1.5:12345"; inputType=InputType.TYPE_CLASS_TEXT }
        AlertDialog.Builder(this).setTitle(R.string.local_share_manual).setView(input)
            .setPositiveButton(R.string.local_share_continue) { _,_ -> val entered=input.text.toString().trim(); lifecycleScope.launch {
                val endpoint=runCatching { withContext(Dispatchers.IO) {
                    val uri=java.net.URI("tcp://"+entered)
                    val host=InetAddress.getByName(uri.host ?: error("Missing address"))
                    require(LocalPairingTransport.isLocal(host) && uri.port in 1..65535)
                    host to uri.port
                } }.getOrNull()
                if(endpoint==null)status.setText(R.string.local_share_failed) else askCode(transfer,endpoint.first,endpoint.second)
            } }.setNegativeButton(android.R.string.cancel,null).show()
    }

    private fun askCode(transfer: SharedWatchTransfer,address:InetAddress,port:Int) {
        val input=EditText(this).apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS; filters=arrayOf(android.text.InputFilter.LengthFilter(14)) }
        AlertDialog.Builder(this).setTitle(R.string.local_share_enter_code).setView(input)
            .setPositiveButton(R.string.local_share_send) { _,_ -> val entered=input.text.toString(); lifecycleScope.launch {
                val sent=runCatching { withContext(Dispatchers.IO) {
                    LocalPairingTransport.send(address,port,entered,transfer.toJson().toByteArray(Charsets.UTF_8))
                } }.getOrDefault(false)
                status.setText(if(sent) R.string.local_share_sent else R.string.local_share_failed)
            } }.setNegativeButton(android.R.string.cancel,null).show()
    }
}
