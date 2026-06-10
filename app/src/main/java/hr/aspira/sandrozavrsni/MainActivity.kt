package hr.aspira.sandrozavrsni

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import hr.aspira.sandrozavrsni.api.HistoryPoint
import hr.aspira.sandrozavrsni.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatDelegate
import android.graphics.Color
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.github.mikephil.charting.components.XAxis



class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private val vm: MainVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        setupCharts()

        // Spinner za odabir raspona
        val labels = resources.getStringArray(R.array.range_labels)
        val minutesValues = resources.getIntArray(R.array.range_minutes)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            labels
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        b.spRange.adapter = adapter

        // default – 2h (120 min)
        val defaultIndex = 2 // 2h
        b.spRange.setSelection(defaultIndex, false)

        b.spRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                val mins = minutesValues[position]
                vm.loadHistory(mins)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.status.collect { b.tvStatus.text = it } }
                launch {
                    vm.now.collect { n ->
                        n ?: return@collect
                        val t = n.temp_c?.let { "%.1f".format(it) } ?: "—"
                        val h = n.hum_pct?.let { "%.1f".format(it) } ?: "—"
                        b.tvNow.text = "Temperatura: $t °C   Vlažnost: $h %"
                    }
                }
                launch { vm.hist.collect { drawHistory(it) } }
            }
        }

        vm.start(pollSec = 2, minutesHistory = 120)
    }

    private fun setupCharts() {
        listOf(b.chartTemp, b.chartHum).forEach { c ->
            c.setBackgroundColor(Color.parseColor("#101010"))
            c.description.isEnabled = false
            c.axisRight.isEnabled = false

            c.legend.textColor = Color.WHITE

            val x = c.xAxis
            x.textColor = Color.LTGRAY
            x.gridColor = Color.DKGRAY
            x.position = XAxis.XAxisPosition.BOTTOM
            x.setDrawAxisLine(true)
            x.setDrawGridLines(true)

            val y = c.axisLeft
            y.textColor = Color.LTGRAY
            y.gridColor = Color.DKGRAY

            // malo dodatne margine da se oznake X-osi ne režu
            c.setExtraOffsets(8f, 8f, 8f, 28f)

            c.setTouchEnabled(true)
            c.setPinchZoom(true)
        }

        b.chartTemp.axisLeft.axisMinimum = -20f
        b.chartTemp.axisLeft.axisMaximum = 60f
        b.chartHum.axisLeft.axisMinimum  = 0f
        b.chartHum.axisLeft.axisMaximum  = 100f
    }

    private fun drawHistory(list: List<HistoryPoint>) {
        if (list.isEmpty()) {
            b.chartTemp.data = null
            b.chartHum.data = null
            b.chartTemp.invalidate()
            b.chartHum.invalidate()
            return
        }

        val t0 = list.first().ts // timestamp prvog uzorka u sekundama
        val tEntries = ArrayList<Entry>(list.size)
        val hEntries = ArrayList<Entry>(list.size)

        list.forEach { p ->
            val x = ((p.ts - t0).toFloat() / 60f) // minute od prvog uzorka
            p.temp_c?.toFloat()?.let { tEntries.add(Entry(x, it)) }
            p.hum_pct?.toFloat()?.let { hEntries.add(Entry(x, it)) }
        }

        val dsT = LineDataSet(tEntries, "Temperatura (°C)").apply {
            color = Color.CYAN
            setDrawCircles(false)
            lineWidth = 2f
            mode = LineDataSet.Mode.LINEAR
        }
        val dsH = LineDataSet(hEntries, "Vlažnost (%)").apply {
            color = Color.GREEN
            setDrawCircles(false)
            lineWidth = 2f
            mode = LineDataSet.Mode.LINEAR
        }

        b.chartTemp.data = LineData(dsT)
        b.chartHum.data  = LineData(dsH)

        // formatter koji pretvara X vrijednost (minute od t0) u HH:mm
        val formatter = TimeAxisFormatter(t0)

        listOf(b.chartTemp, b.chartHum).forEach { chart ->
            chart.xAxis.valueFormatter = formatter
            chart.xAxis.granularity = 1f
            chart.xAxis.isGranularityEnabled = true
            chart.xAxis.labelRotationAngle = -25f
            chart.xAxis.setAvoidFirstLastClipping(true)
        }

        b.chartTemp.invalidate()
        b.chartHum.invalidate()
    }
}

class TimeAxisFormatter(private val t0Seconds: Long) : ValueFormatter() {
    private val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun getFormattedValue(value: Float): String {
        // value je "minute od prvog uzorka"
        val seconds = t0Seconds + (value * 60).toLong()
        return sdf.format(Date(seconds * 1000))
    }
}

