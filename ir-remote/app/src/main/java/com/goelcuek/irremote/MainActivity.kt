package com.goelcuek.irremote

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------ palette
private val Bg = Color(0xFF101114)
private val Panel = Color(0xFF1A1C21)
private val KeyBg = Color(0xFF26292F)
private val Fg = Color(0xFFF2F2F5)
private val Dim = Color(0xFF8B8F98)
private val Accent = Color(0xFFFF5A4E)
private val Ok = Color(0xFF3DDC84)

private val POPULAR = listOf(
    "Samsung", "LG", "Sony", "Philips", "Toshiba", "TCL", "Hisense", "Grundig",
    "Panasonic", "Sharp", "Telefunken", "Hitachi", "JVC", "Xiaomi", "Thomson"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent, background = Bg, surface = Panel,
                    onSurface = Fg, onBackground = Fg
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = Bg) { App() }
            }
        }
    }
}

/** Holds the active TV code set and sends keys; stable across recompositions. */
class RemoteCtl(private val tx: IrTransmitter, private val onMissing: () -> Unit) {
    var set by mutableStateOf<CodeSet?>(null)
    var flash by mutableIntStateOf(0)
    fun has(key: String) = set?.keys?.containsKey(key) == true
    fun send(key: String) {
        val sig = set?.keys?.get(key)
        if (sig == null) { onMissing(); return }
        flash++
        tx.send(sig)
    }
    fun sendRaw(sig: IrSignal) { flash++; tx.send(sig) }
}

private sealed interface Screen {
    data object Remote : Screen
    /** kind == null → ask TV or AC first. editing = index being re-tuned, or -1. */
    data class Setup(val kind: String? = null, val editing: Int = -1) : Screen
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val tx = remember { IrTransmitter(ctx) }
    val store = remember { RemoteStore(ctx) }
    var db by remember { mutableStateOf<CodeDb?>(null) }
    var remotes by remember { mutableStateOf(store.load()) }
    var active by remember { mutableIntStateOf(store.active.coerceIn(0, maxOf(0, remotes.size - 1))) }
    var screen by remember { mutableStateOf<Screen>(if (remotes.isEmpty()) Screen.Setup() else Screen.Remote) }
    val ctl = remember {
        RemoteCtl(tx) { Toast.makeText(ctx, "This button isn't in the current code set", Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(Unit) { db = withContext(Dispatchers.IO) { CodeDb.load(ctx) } }
    LaunchedEffect(db, remotes, active) {
        val d = db ?: return@LaunchedEffect
        val r = remotes.getOrNull(active)
        ctl.set = if (r != null && !r.isAc) d.codeSet(r.brand, r.setIndex) else null
    }

    val database = db
    if (database == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
        return
    }

    fun saveList(list: List<SavedRemote>) { remotes = list; store.save(list) }

    Column(Modifier.fillMaxSize()) {
        if (!tx.available) {
            Text(
                "No IR blaster detected on this phone — buttons won't reach the device.",
                color = Fg, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().background(Color(0xFF5A1F1B)).padding(10.dp)
            )
        }
        when (val s = screen) {
            is Screen.Remote -> {
                val current = remotes.getOrNull(active)
                if (current == null) { screen = Screen.Setup(); return@Column }
                Column(Modifier.fillMaxSize()) {
                    DeviceHeader(
                        ctl = ctl, remotes = remotes, active = active,
                        subtitle = if (current.isAc) "${AcBrand.of(current.brand).label} · air conditioner"
                        else "${current.brand} · code ${current.setIndex + 1}/${database.setCount(current.brand)}" +
                            (ctl.set?.model?.let { " · $it" } ?: ""),
                        onSelect = { active = it; store.active = it },
                        onAdd = { screen = Screen.Setup() },
                        onRetune = { screen = Screen.Setup(current.kind, editing = active) },
                        onDelete = {
                            val list = remotes.toMutableList().also { l -> l.removeAt(active) }
                            saveList(list)
                            active = 0; store.active = 0
                            if (list.isEmpty()) screen = Screen.Setup()
                        }
                    )
                    if (current.isAc) {
                        AcScreen(
                            remote = current, ctl = ctl,
                            onState = { st ->
                                val list = remotes.toMutableList()
                                if (active in list.indices) { list[active] = list[active].copy(ac = st); saveList(list) }
                            }
                        )
                    } else {
                        TvRemoteBody(ctl)
                    }
                }
            }
            is Screen.Setup -> {
                val editingRemote = remotes.getOrNull(s.editing)
                SetupFlow(
                    db = database, tx = tx, initial = s, editingRemote = editingRemote,
                    canCancel = remotes.isNotEmpty(),
                    onCancel = { screen = Screen.Remote },
                    onDone = { r ->
                        val list = remotes.toMutableList()
                        val pos = if (s.editing in list.indices) { list[s.editing] = r; s.editing }
                                  else { list.add(r); list.size - 1 }
                        saveList(list)
                        active = pos; store.active = pos
                        screen = Screen.Remote
                    }
                )
            }
        }
    }
}

// ================================================================== header

@Composable
private fun DeviceHeader(
    ctl: RemoteCtl, remotes: List<SavedRemote>, active: Int, subtitle: String,
    onSelect: (Int) -> Unit, onAdd: () -> Unit, onRetune: () -> Unit, onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val current = remotes.getOrNull(active)

    // brief red glow whenever a signal goes out
    var glow by remember { mutableStateOf(false) }
    LaunchedEffect(ctl.flash) { if (ctl.flash > 0) { glow = true; delay(120); glow = false } }
    val glowAlpha by animateFloatAsState(if (glow) 1f else 0.15f, tween(if (glow) 30 else 250), label = "glow")

    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Accent.copy(alpha = glowAlpha)))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { menuOpen = true }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f, fill = false)) {
                    Text(current?.name ?: "", color = Fg, fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.ArrowDropDown, null, tint = Dim)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                remotes.forEachIndexed { i, r ->
                    DropdownMenuItem(
                        text = { Text(r.name) },
                        leadingIcon = { Icon(if (r.isAc) Icons.Rounded.AcUnit else Icons.Rounded.Tv, null) },
                        trailingIcon = { if (i == active) Icon(Icons.Rounded.Check, null, tint = Ok) },
                        onClick = { menuOpen = false; onSelect(i) }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Add a TV or AC") },
                    leadingIcon = { Icon(Icons.Rounded.Add, null) },
                    onClick = { menuOpen = false; onAdd() })
                DropdownMenuItem(text = { Text(if (current?.isAc == true) "Change AC protocol" else "Change code for this TV") },
                    leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                    onClick = { menuOpen = false; onRetune() })
                DropdownMenuItem(text = { Text("Remove ${current?.name ?: ""}") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = { menuOpen = false; confirmDelete = true })
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${current?.name}?") },
            confirmButton = { TextButton({ confirmDelete = false; onDelete() }) { Text("Remove") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

// ================================================================== TV remote

@Composable
private fun TvRemoteBody(ctl: RemoteCtl) {
    var showNumbers by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconKey("power", ctl, Icons.Rounded.PowerSettingsNew, bg = Accent, size = 64.dp, shape = CircleShape)
            IconKey("input", ctl, Icons.Rounded.Input, size = 64.dp, shape = CircleShape)
            IconKey("mute", ctl, Icons.Rounded.VolumeOff, size = 64.dp, shape = CircleShape)
        }

        Spacer(Modifier.height(26.dp))
        DPad(ctl)
        Spacer(Modifier.height(22.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconKey("back", ctl, Icons.AutoMirrored.Rounded.ArrowBack, size = 60.dp, shape = CircleShape)
            IconKey("home", ctl, Icons.Rounded.Home, size = 60.dp, shape = CircleShape)
            IconKey("menu", ctl, Icons.Rounded.Menu, size = 60.dp, shape = CircleShape)
            TextKey("exit", ctl, "EXIT", Modifier.size(60.dp), shape = CircleShape)
        }

        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Rocker(ctl, "vol_up", "vol_dn", "VOL")
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IconKey("info", ctl, Icons.Rounded.Info, size = 56.dp, shape = CircleShape)
                TextKey("guide", ctl, "GUIDE", Modifier.size(56.dp), shape = CircleShape, fontSize = 11)
            }
            Rocker(ctl, "ch_up", "ch_dn", "CH")
        }

        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Toggle("123", Icons.Rounded.Dialpad, showNumbers, Modifier.weight(1f)) { showNumbers = !showNumbers }
            Toggle("More", Icons.Rounded.Apps, showMore, Modifier.weight(1f)) { showMore = !showMore }
        }

        AnimatedVisibility(showNumbers) {
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { TextKey(it, ctl, it, Modifier.weight(1f).height(54.dp), fontSize = 20) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextKey("text", ctl, "TXT", Modifier.weight(1f).height(54.dp), fontSize = 13)
                    TextKey("0", ctl, "0", Modifier.weight(1f).height(54.dp), fontSize = 20)
                    TextKey("subtitle", ctl, "SUB", Modifier.weight(1f).height(54.dp), fontSize = 13)
                }
            }
        }

        AnimatedVisibility(showMore) {
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("red" to Color(0xFFE5484D), "green" to Color(0xFF30A46C),
                        "yellow" to Color(0xFFF5D90A), "blue" to Color(0xFF3E63DD)).forEach { (k, c) ->
                        RKey(k, ctl, Modifier.weight(1f).height(34.dp), bg = c.copy(alpha = 0.85f)) {}
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconKey("rew", ctl, Icons.Rounded.FastRewind, Modifier.weight(1f))
                    IconKey("play", ctl, Icons.Rounded.PlayArrow, Modifier.weight(1f))
                    IconKey("pause", ctl, Icons.Rounded.Pause, Modifier.weight(1f))
                    IconKey("stop", ctl, Icons.Rounded.Stop, Modifier.weight(1f))
                    IconKey("ff", ctl, Icons.Rounded.FastForward, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextKey("netflix", ctl, "NETFLIX", Modifier.weight(1f).height(48.dp),
                        bg = Color(0xFF3A1214), fg = Color(0xFFE50914), fontSize = 13)
                    TextKey("youtube", ctl, "YouTube", Modifier.weight(1f).height(48.dp),
                        bg = Color(0xFF2F2F33), fontSize = 13)
                    IconKey("sleep", ctl, Icons.Rounded.Bedtime, Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun DPad(ctl: RemoteCtl) {
    Box(
        Modifier.size(250.dp).clip(CircleShape).background(Panel)
            .border(1.dp, Color(0xFF2E3138), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val arrow = Modifier.size(78.dp)
        IconKey("up", ctl, Icons.Rounded.KeyboardArrowUp, arrow.align(Alignment.TopCenter),
            bg = Color.Transparent, repeat = true, iconSize = 38.dp)
        IconKey("down", ctl, Icons.Rounded.KeyboardArrowDown, arrow.align(Alignment.BottomCenter),
            bg = Color.Transparent, repeat = true, iconSize = 38.dp)
        IconKey("left", ctl, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, arrow.align(Alignment.CenterStart),
            bg = Color.Transparent, repeat = true, iconSize = 38.dp)
        IconKey("right", ctl, Icons.AutoMirrored.Rounded.KeyboardArrowRight, arrow.align(Alignment.CenterEnd),
            bg = Color.Transparent, repeat = true, iconSize = 38.dp)
        TextKey("ok", ctl, "OK", Modifier.size(96.dp), shape = CircleShape, fontSize = 18)
    }
}

@Composable
private fun Rocker(ctl: RemoteCtl, upKey: String, downKey: String, label: String) {
    Column(
        Modifier.width(78.dp).clip(RoundedCornerShape(39.dp)).background(KeyBg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconKey(upKey, ctl, Icons.Rounded.Add, Modifier.fillMaxWidth().height(70.dp),
            bg = Color.Transparent, repeat = true)
        Text(label, color = Dim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        IconKey(downKey, ctl, Icons.Rounded.Remove, Modifier.fillMaxWidth().height(70.dp),
            bg = Color.Transparent, repeat = true)
    }
}

@Composable
private fun Toggle(label: String, icon: ImageVector, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (on) Color(0xFF33363E) else Panel).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Fg, fontSize = 14.sp)
        Icon(if (on) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Dim)
    }
}

// ================================================================== TV keys

@Composable
private fun RKey(
    key: String, ctl: RemoteCtl, modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp), bg: Color = KeyBg,
    repeat: Boolean = false, content: @Composable () -> Unit
) {
    val enabled = ctl.has(key)
    var pressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val pressBg = if (bg == Color.Transparent) Color.White.copy(alpha = 0.08f)
                  else androidx.compose.ui.graphics.lerp(bg, Color.White, 0.14f)
    Box(
        modifier
            .graphicsLayer {
                val s = if (pressed) 0.93f else 1f
                scaleX = s; scaleY = s
                alpha = if (enabled) 1f else 0.28f
            }
            .clip(shape)
            .background(if (pressed) pressBg else bg)
            .pointerInput(key, repeat) {
                detectTapGestures(onPress = {
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    ctl.send(key)
                    val job = if (repeat && ctl.has(key)) scope.launch {
                        delay(450)
                        while (isActive) {
                            ctl.send(key)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            delay(150)
                        }
                    } else null
                    tryAwaitRelease()
                    job?.cancel()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun IconKey(
    key: String, ctl: RemoteCtl, icon: ImageVector, modifier: Modifier = Modifier,
    bg: Color = KeyBg, size: Dp? = null,
    shape: Shape = RoundedCornerShape(16.dp), repeat: Boolean = false,
    iconSize: Dp = 28.dp
) {
    val m = if (size != null) modifier.size(size) else modifier.height(54.dp)
    RKey(key, ctl, m, shape, bg, repeat) {
        Icon(icon, contentDescription = key, tint = Fg, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun TextKey(
    key: String, ctl: RemoteCtl, label: String, modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp), bg: Color = KeyBg, fg: Color = Fg, fontSize: Int = 14
) {
    RKey(key, ctl, modifier, shape, bg) {
        Text(label, color = fg, fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ================================================================== AC remote

private fun modeColor(m: AcMode) = when (m) {
    AcMode.COOL -> Color(0xFF4EA8FF)
    AcMode.HEAT -> Color(0xFFFF8A3D)
    AcMode.DRY -> Color(0xFF3DD6C6)
    AcMode.FAN -> Color(0xFFB8BEC9)
    AcMode.AUTO -> Color(0xFFA78BFA)
}

private fun modeIcon(m: AcMode) = when (m) {
    AcMode.AUTO -> Icons.Rounded.Autorenew
    AcMode.COOL -> Icons.Rounded.AcUnit
    AcMode.DRY -> Icons.Rounded.WaterDrop
    AcMode.FAN -> Icons.Rounded.Air
    AcMode.HEAT -> Icons.Rounded.WbSunny
}

@Composable
private fun AcScreen(remote: SavedRemote, ctl: RemoteCtl, onState: (AcState) -> Unit) {
    val brand = AcBrand.of(remote.brand)
    val s = remote.ac
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Job?>(null) }

    fun tap() = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

    /** Settings changes are debounced so rapid +/+/+ taps send one final state. */
    fun change(new: AcState) {
        tap()
        onState(new)
        if (!new.power) return
        pending?.cancel()
        pending = scope.launch {
            delay(350)
            ctl.sendRaw(AcEncoder.state(brand, new, powerChanged = false))
        }
    }

    fun togglePower() {
        tap()
        pending?.cancel()
        val new = s.copy(power = !s.power)
        onState(new)
        ctl.sendRaw(AcEncoder.state(brand, new, powerChanged = true))
    }

    fun toggleSwing() {
        tap()
        val new = s.copy(swing = !s.swing)
        onState(new)
        if (new.power) { pending?.cancel(); ctl.sendRaw(AcEncoder.swing(brand, new)) }
    }

    val tint by animateColorAsState(if (s.power) modeColor(s.mode) else Dim, tween(300), label = "tint")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(10.dp))

        // ---- status panel
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.22f), Panel)))
                .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(32.dp))
                .padding(vertical = 26.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(modeIcon(s.mode), null, tint = tint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (s.power) s.mode.label.uppercase() else "OFF", color = tint,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
                Row(verticalAlignment = Alignment.Top) {
                    Text("${s.temp}", color = if (s.power) Fg else Dim, fontSize = 96.sp,
                        fontWeight = FontWeight.Light)
                    Text("°C", color = Dim, fontSize = 24.sp, modifier = Modifier.padding(top = 22.dp))
                }
                Text(
                    "Fan ${s.fan.label}  ·  Swing ${if (s.swing) "on" else "off"}",
                    color = Dim, fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // ---- temp / power
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AcRoundButton(Icons.Rounded.Remove, "Colder", 72.dp, KeyBg,
                enabled = s.temp > AcBrand.MIN_TEMP) { change(s.copy(temp = s.temp - 1)) }
            AcRoundButton(Icons.Rounded.PowerSettingsNew, "Power", 92.dp,
                if (s.power) Accent else KeyBg) { togglePower() }
            AcRoundButton(Icons.Rounded.Add, "Warmer", 72.dp, KeyBg,
                enabled = s.temp < AcBrand.MAX_TEMP) { change(s.copy(temp = s.temp + 1)) }
        }

        Spacer(Modifier.height(24.dp))

        // ---- mode
        AcLabel("Mode")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AcMode.entries.forEach { m ->
                AcChip(m.label, modeIcon(m), s.mode == m, modeColor(m), Modifier.weight(1f)) {
                    change(s.copy(mode = m))
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- fan
        AcLabel("Fan speed")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AcFan.entries.forEach { f ->
                val icon = when (f) {
                    AcFan.AUTO -> Icons.Rounded.Autorenew
                    AcFan.LOW -> Icons.Rounded.SignalCellularAlt1Bar
                    AcFan.MED -> Icons.Rounded.SignalCellularAlt2Bar
                    AcFan.HIGH -> Icons.Rounded.SignalCellularAlt
                }
                AcChip(if (f == AcFan.MED) "Med" else f.label, icon, s.fan == f, Fg, Modifier.weight(1f)) {
                    change(s.copy(fan = f))
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- swing
        Row(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
                .background(if (s.swing) Color(0xFF33363E) else Panel).clickable { toggleSwing() }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.SwapVert, null, tint = Fg)
            Spacer(Modifier.width(12.dp))
            Text("Swing", color = Fg, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(checked = s.swing, onCheckedChange = { toggleSwing() })
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "The phone can't read the AC's real state. If the screen and the AC get out of sync, " +
                "press Power twice to bring them back in line.",
            color = Dim, fontSize = 12.sp, lineHeight = 17.sp
        )
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun AcLabel(text: String) {
    Text(text.uppercase(), color = Dim, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp))
}

@Composable
private fun AcRoundButton(
    icon: ImageVector, desc: String, size: Dp, bg: Color,
    enabled: Boolean = true, onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier.size(size)
            .graphicsLayer {
                val k = if (pressed) 0.92f else 1f
                scaleX = k; scaleY = k
                alpha = if (enabled) 1f else 0.3f
            }
            .clip(CircleShape).background(bg)
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { if (enabled) onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Fg, modifier = Modifier.size(size * 0.42f)) }
}

@Composable
private fun AcChip(
    label: String, icon: ImageVector, selected: Boolean, color: Color,
    modifier: Modifier, onClick: () -> Unit
) {
    val bg by animateColorAsState(if (selected) color.copy(alpha = 0.22f) else Panel, tween(200), label = "chip")
    Column(
        modifier.height(66.dp).clip(RoundedCornerShape(16.dp)).background(bg)
            .border(1.dp, if (selected) color.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (selected) color else Dim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (selected) Fg else Dim, fontSize = 12.sp, maxLines = 1)
    }
}

// ================================================================== setup

@Composable
private fun SetupFlow(
    db: CodeDb, tx: IrTransmitter, initial: Screen.Setup, editingRemote: SavedRemote?,
    canCancel: Boolean, onCancel: () -> Unit, onDone: (SavedRemote) -> Unit
) {
    var kind by remember { mutableStateOf(initial.kind) }
    var tvBrand by remember { mutableStateOf(if (editingRemote?.isAc == false) editingRemote.brand else null) }

    BackHandler(enabled = kind != null || canCancel) {
        when {
            kind == SavedRemote.KIND_TV && tvBrand != null && editingRemote == null -> tvBrand = null
            kind != null && editingRemote == null -> { kind = null; tvBrand = null }
            else -> onCancel()
        }
    }

    when (kind) {
        null -> KindPicker(canCancel, onCancel) { kind = it }
        SavedRemote.KIND_AC -> AcSetup(
            tx = tx, editing = editingRemote,
            onBack = { if (editingRemote == null) kind = null else onCancel() },
            onDone = onDone
        )
        else -> {
            val b = tvBrand
            if (b == null) {
                BrandPicker(db, onBack = { if (editingRemote == null) kind = null else onCancel() }) { tvBrand = it }
            } else {
                CodeFinder(db, tx, b, if (editingRemote?.brand == b) editingRemote.setIndex else 0,
                    onBack = { if (editingRemote == null) tvBrand = null else onCancel() },
                    onDone = { name, idx -> onDone(SavedRemote(name, b, idx, SavedRemote.KIND_TV)) })
            }
        }
    }
}

@Composable
private fun KindPicker(canCancel: Boolean, onCancel: () -> Unit, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("What do you want to control?", color = Fg, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            if (canCancel) IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Close", tint = Fg) }
        }
        Spacer(Modifier.height(24.dp))
        KindCard(Icons.Rounded.Tv, "TV", "117 brands — find the working code step by step", Accent) {
            onPick(SavedRemote.KIND_TV)
        }
        Spacer(Modifier.height(14.dp))
        KindCard(Icons.Rounded.AcUnit, "Air conditioner", "Samsung and LG — mode, temperature, fan, swing",
            Color(0xFF4EA8FF)) { onPick(SavedRemote.KIND_AC) }
    }
}

@Composable
private fun KindCard(icon: ImageVector, title: String, sub: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Panel).clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Dim, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Dim)
    }
}

@Composable
private fun AcSetup(
    tx: IrTransmitter, editing: SavedRemote?, onBack: () -> Unit, onDone: (SavedRemote) -> Unit
) {
    var brand by remember {
        mutableStateOf(if (editing?.isAc == true) AcBrand.of(editing.brand) else AcBrand.SAMSUNG)
    }
    var lastOn by remember { mutableStateOf(editing?.ac?.power ?: false) }
    var naming by remember { mutableStateOf(false) }
    val view = LocalView.current
    val testState = (editing?.ac ?: AcState()).copy(mode = AcMode.COOL, temp = 24, fan = AcFan.AUTO)

    fun test(on: Boolean) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        lastOn = on
        tx.send(AcEncoder.state(brand, testState.copy(power = on), powerChanged = true))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Fg) }
            Text("Air conditioner", color = Fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        AcLabel("Brand")
        AcBrand.entries.forEach { b ->
            val sel = b == brand
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (sel) Color(0xFF1F2A38) else Panel)
                    .border(1.dp, if (sel) Color(0xFF4EA8FF) else Color.Transparent, RoundedCornerShape(18.dp))
                    .clickable { brand = b }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = sel, onClick = { brand = b })
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(b.label, color = Fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(b.hint, color = Dim, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Point the top of the phone at the AC's indoor unit and test. " +
                "You should hear a beep. If nothing happens with LG, try the other LG option.",
            color = Dim, fontSize = 14.sp, lineHeight = 20.sp
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { test(true) }, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4EA8FF), contentColor = Color(0xFF06182B)),
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Icon(Icons.Rounded.AcUnit, null)
                Spacer(Modifier.width(6.dp))
                Text("On · Cool 24°")
            }
            Button(
                onClick = { test(false) }, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KeyBg, contentColor = Fg),
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Icon(Icons.Rounded.PowerSettingsNew, null)
                Spacer(Modifier.width(6.dp))
                Text("Turn off")
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { naming = true },
            colors = ButtonDefaults.buttonColors(containerColor = Ok, contentColor = Color(0xFF0B1F12)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Rounded.Check, null)
            Spacer(Modifier.width(8.dp))
            Text("It works", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(30.dp))
    }

    if (naming) {
        var name by remember { mutableStateOf(editing?.name ?: "${brand.label.substringBefore(" (")} AC") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Name this AC") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton({
                    naming = false
                    val st = if (editing?.isAc == true) editing.ac.copy(power = lastOn)
                             else testState.copy(power = lastOn)
                    onDone(SavedRemote(name.ifBlank { "AC" }, brand.id, 0, SavedRemote.KIND_AC, st))
                }) { Text("Save") }
            },
            dismissButton = { TextButton({ naming = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BrandPicker(db: CodeDb, onBack: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val popular = remember(db) { POPULAR.filter { p -> db.brands.any { it.equals(p, true) } } }
    val filtered = remember(query, db) {
        if (query.isBlank()) db.brands else db.brands.filter { it.contains(query.trim(), ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Fg) }
            Text("Which TV brand?", color = Fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text("Search ${db.brands.size} brands") },
            singleLine = true, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            if (query.isBlank()) {
                item { SectionLabel("Popular") }
                items(popular) { BrandRow(it, db.setCount(it)) { onPick(it) } }
                item { SectionLabel("All brands") }
            }
            items(filtered) { BrandRow(it, db.setCount(it)) { onPick(it) } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = Dim, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp, start = 4.dp))
}

@Composable
private fun BrandRow(name: String, sets: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Tv, null, tint = Dim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(name, color = Fg, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text("$sets code${if (sets == 1) "" else "s"}", color = Dim, fontSize = 12.sp)
    }
}

@Composable
private fun CodeFinder(
    db: CodeDb, tx: IrTransmitter, brand: String, startIndex: Int,
    onBack: () -> Unit, onDone: (String, Int) -> Unit
) {
    val count = db.setCount(brand)
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, maxOf(0, count - 1))) }
    val set = remember(index) { db.codeSet(brand, index) }
    var naming by remember { mutableStateOf(false) }
    val view = LocalView.current
    val ctx = LocalContext.current

    fun test(key: String) {
        val sig = set?.keys?.get(key)
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (sig == null) Toast.makeText(ctx, "This code set has no ${key.replace('_', ' ')} button", Toast.LENGTH_SHORT).show()
        else tx.send(sig)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Fg) }
            Text(brand, color = Fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(18.dp))
        Text("Code ${index + 1} of $count", color = Fg, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(set?.model ?: "", color = Dim, fontSize = 13.sp)
        Text("${set?.keys?.size ?: 0} buttons", color = Dim, fontSize = 12.sp)

        Spacer(Modifier.height(24.dp))
        Text(
            "Point the top of the phone at the TV and press a test button. " +
                "If the TV reacts, tap “It works”. If not, try the next code.",
            color = Dim, fontSize = 14.sp, lineHeight = 20.sp
        )
        Spacer(Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            TestButton("Power", Icons.Rounded.PowerSettingsNew, Accent, 92.dp) { test("power") }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TestButton("Vol +", Icons.Rounded.VolumeUp, KeyBg, 64.dp) { test("vol_up") }
                TestButton("Mute", Icons.Rounded.VolumeOff, KeyBg, 64.dp) { test("mute") }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Tip: Vol + and Mute are safer tests than Power.", color = Dim, fontSize = 12.sp)

        Spacer(Modifier.height(34.dp))
        Button(
            onClick = { naming = true },
            colors = ButtonDefaults.buttonColors(containerColor = Ok, contentColor = Color(0xFF0B1F12)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Rounded.Check, null)
            Spacer(Modifier.width(8.dp))
            Text("It works", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { index = (index - 1 + count) % count },
                enabled = count > 1, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(52.dp)
            ) { Text("Previous") }
            Button(
                onClick = { index = (index + 1) % count },
                enabled = count > 1, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KeyBg, contentColor = Fg),
                modifier = Modifier.weight(1f).height(52.dp)
            ) { Text("Next code") }
        }
        Spacer(Modifier.height(30.dp))
    }

    if (naming) {
        var name by remember { mutableStateOf("$brand TV") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Name this TV") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton({ naming = false; onDone(name.ifBlank { "$brand TV" }, index) }) { Text("Save") }
            },
            dismissButton = { TextButton({ naming = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TestButton(label: String, icon: ImageVector, bg: Color, size: Dp, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(icon, label, tint = Fg, modifier = Modifier.size(size * 0.42f)) }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Dim, fontSize = 12.sp)
    }
}
