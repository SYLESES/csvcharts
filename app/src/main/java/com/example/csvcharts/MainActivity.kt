---
## `MainActivity.kt`
```kotlin
package com.example.csvcharts

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var parsedFiles by remember { mutableStateOf<List<ParsedCsv>>(emptyList()) }

    val openDocs = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            val list = uris.mapNotNull { uri ->
                runCatching { parseCsvFromUri(ctx.contentResolver, uri) }.getOrNull()
            }
            parsedFiles = list
        }
    )

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = "Visualisation graphique de fichiers CSV",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        openDocs.launch(arrayOf("text/*", "text/csv", "application/octet-stream"))
                    }) { Text("Sélectionner des fichiers") }
                    Spacer(Modifier.width(12.dp))
                    if (parsedFiles.isNotEmpty()) {
                        Text("${'$'}{parsedFiles.size} fichier(s) chargé(s)")
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (parsedFiles.isEmpty()) {
                    AssistChip(onClick = {
                        openDocs.launch(arrayOf("text/*", "text/csv", "application/octet-stream"))
                    }, label = { Text("Importez un ou plusieurs fichiers (même sans extension CSV) pour afficher les graphiques.") })
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        parsedFiles.forEach { file ->
                            Text(file.displayTitle, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))

                            val groups = buildGroups(file)
                            groups.forEach { group ->
                                ChartCard(group, Modifier.fillMaxWidth().padding(bottom = 12.dp))
                            }
                            Divider(Modifier.padding(vertical = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChartCard(group: ChartGroup, modifier: Modifier = Modifier) {
    Card(modifier, elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(group.title, fontWeight = FontWeight.Medium)
            if (group.series.isEmpty()) {
                Text("• ${'$'}{group.title} : colonne(s) absente(s)")
                return@Column
            }
            Spacer(Modifier.height(8.dp))
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(220.dp),
                factory = { context -> LineChart(context) },
                update = { chart ->
                    val dataSets = group.series.map { s ->
                        val entries = s.values.mapIndexedNotNull { idx, v -> v?.let { Entry(idx.toFloat(), it.toFloat()) } }
                        LineDataSet(entries, s.label).apply {
                            axisDependency = YAxis.AxisDependency.LEFT
                            setDrawCircles(false)
                            lineWidth = 1.8f
                            setDrawValues(false)
                        }
                    }
                    chart.data = LineData(dataSets)
                    chart.description = Description().apply { text = "Index (numéro de ligne)" }
                    chart.axisRight.isEnabled = false
                    chart.axisLeft.apply {
                        setDrawGridLines(true)
                        granularity = 1f
                    }
                    chart.xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        setDrawGridLines(true)
                        granularity = 1f
                    }
                    chart.legend.apply {
                        isEnabled = true
                        verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                        horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                        textSize = 10f
                        isWordWrapEnabled = true
                    }
                    chart.setPinchZoom(true)
                    chart.invalidate()
                }
            )
        }
    }
}

// --- Data structures ---

data class ParsedCsv(
    val fileName: String,
    val headers: List<String>,
    val units: Map<String, String>,
    val columns: Map<String, List<Double?>>, // column -> values (nullable when not numeric)
    val displayTitle: String
)

data class Series(val label: String, val values: List<Double?>)

data class ChartGroup(val title: String, val unit: String?, val series: List<Series>)

// --- Parsing ---

fun parseCsvFromUri(cr: ContentResolver, uri: Uri): ParsedCsv {
    val name = guessDisplayName(cr, uri)
    val now = SimpleDateFormat("yy_MM_dd_HH'h'mm", Locale.getDefault()).format(Date())
    val title = "Chargé pour affichage le ${'$'}now - ${'$'}name"

    val lines = readAllLines(cr, uri)
    require(lines.isNotEmpty()) { "Fichier vide" }

    val sep = detectSeparator(lines.first())
    var headers = splitKeepEmpty(lines[0], sep).map { it.trim() }
    var units = if (lines.size > 1) splitKeepEmpty(lines[1], sep).map { it.trim() } else emptyList()

    if (headers.isNotEmpty() && headers.last().isBlank()) {
        headers = headers.dropLast(1)
        if (units.size >= headers.size + 1) units = units.dropLast(1)
    }

    val unitMap = headers.indices.associate { idx ->
        val key = headers[idx]
        val u = units.getOrNull(idx)?.trim().orElse("")
        key to u
    }

    val dataLines = if (lines.size >= 3) lines.drop(2) else emptyList()
    val rows = dataLines.map { splitKeepEmpty(it, sep) }

    val colMap = headers.indices.associate { colIdx ->
        val h = headers[colIdx]
        val values = rows.map { row ->
            val raw = row.getOrNull(colIdx)?.trim().orElse("")
            val normalized = raw.replace(',', '.')
            normalized.toDoubleOrNull()
        }
        h to values
    }

    return ParsedCsv(
        fileName = name,
        headers = headers,
        units = unitMap,
        columns = colMap,
        displayTitle = title
    )
}

fun detectSeparator(sampleLine: String): Char {
    val candidates = listOf(';', ',', ':', '\t', '|')
    return candidates.maxByOrNull { c -> sampleLine.count { it == c } } ?: ';'
}

fun splitKeepEmpty(line: String, sep: Char): List<String> {
    // Conserve les champs vides (y compris en fin de ligne)
    val parts = mutableListOf<String>()
    var start = 0
    for (i in line.indices) {
        if (line[i] == sep) {
            parts.add(line.substring(start, i))
            start = i + 1
        }
    }
    parts.add(line.substring(start))
    return parts
}

fun readAllLines(cr: ContentResolver, uri: Uri): List<String> =
    cr.openInputStream(uri).use { ins ->
        requireNotNull(ins) { "Impossible d'ouvrir le fichier" }
        BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).use { br ->
            br.lineSequence().toList()
        }
    }

fun guessDisplayName(cr: ContentResolver, uri: Uri): String {
    return runCatching {
        var name: String? = null
        val cursor: Cursor? = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) name = it.getString(0)
        }
        name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "fichier"
    }.getOrDefault("fichier")
}

// --- Grouping logic (identique à votre Python) ---

fun buildGroups(file: ParsedCsv): List<ChartGroup> {
    val groups: List<Pair<String, List<String>>> = listOf(
        "EGT1 a EGT8" to listOf("EGT1","EGT2","EGT3","EGT4","EGT5","EGT6","EGT7","EGT8"),
        "Oil-Pressur et Fuel-P" to listOf("oil-Pressur","fuel-P"),
        "Admission" to listOf("admission"),
        "Etage-2" to listOf("etage-2"),
        "Turbo_Oil, Exhaut_R, Exhaust_L" to listOf("Turbo_Oil","Exhaut_R","Exhaust_L")
    )

    val groupedCols = groups.flatMap { it.second }
    val remaining = file.headers.filter { it !in groupedCols }
    val traces = groups + remaining.map { it to listOf(it) }

    return traces.map { (title, cols) ->
        val exist = cols.filter { it in file.columns.keys }
        val unit = exist.firstOrNull()?.let { file.units[it] }?.takeIf { !it.isNullOrBlank() }
        val series = exist.mapNotNull { col ->
            val values = file.columns[col] ?: return@mapNotNull null
            if (values.all { it == null }) null else Series(label = buildLabel(col, file.units[col]), values = values)
        }
        ChartGroup(title = if (unit.isNullOrBlank()) title else "$title [${'$'}unit]", unit = unit, series = series)
    }
}

fun buildLabel(col: String, unit: String?): String = if (!unit.isNullOrBlank()) "$col (${ '$' }unit)" else col

// --- Extensions utilitaires ---

private fun String?.orElse(fallback: String) = this ?: fallback
```

---
## Notes
- **Compatibilité séparateurs** : `; , : TAB |` (détection naïve sur la 1ère ligne).  
- **Nettoyage numérique** : remplace `,` par `.` puis `toDoubleOrNull()`. Les valeurs non-numériques sont ignorées (trous de série).  
- **Affichage** : une carte par groupe/colonne restante, légende pliable, zoom/pinch.  
- **Évolutions faciles** : export PNG/PDF, changement palette, filtrage colonnes, lecture *streaming* pour très gros fichiers.
