/*
 * Copyright (C) 2017 The OmniROM Project
 * Copyright (C) 2022 Benzo Rom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.*
import android.os.Looper.*
import android.os.ServiceManager.checkService
import android.service.dreams.DreamService
import android.service.dreams.IDreamManager
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import java.io.BufferedReader
import java.io.FileReader
import java.lang.Exception
import java.lang.NumberFormatException
import java.lang.StringBuilder
import kotlin.math.roundToInt

open class CPUInfoService : Service() {

    companion object {
        private const val logTag = "CPUInfoService"
        private const val isDebug = false
        private const val numberOfCpusPath = "/sys/devices/system/cpu/present"
        private const val currentCpu = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"
        private const val cpuRoot = "/sys/devices/system/cpu/cpu"
        private const val cpuCurrentTail = "/cpufreq/scaling_cur_freq"
        private const val cpuGovernorTail = "/cpufreq/scaling_governor"
        private fun readOneLine(fname: String): String? {
            val reader: BufferedReader
            val line: String?
            try {
                reader = BufferedReader(FileReader(fname), 512)
                line = reader.use { br -> br.readLine() }
            } catch (ex: Exception) {
                return null
            }
            return line
        }
    }

    private var view: View? = null
    private var currentCPUThread: Thread? = null
    private var numberOfCpus = 2
    private var cpu: Array<String?>? = null
    private var currentFreq: Array<String?>? = null
    private var currentGov: Array<String?>? = null
    private var cpuTempDivider = 1
    private var cpuTempSensor = ""
    private var displayCpus = ""
    private var cpuTempAvailable = false
    private var textHeight = 0
    private var dreamManager: IDreamManager? = null

    private inner class CPUView(context: Context) : View(context) {
        private val onlinePaint: Paint
        private val offlinePaint: Paint
        private val ascent: Float
        private val fh: Int
        private val maxWidth: Int
        private var neededWidth = 0
        private var neededHeight = 0
        private var cpuTemperature: String? = null
        private var dataAvailable = false
        private val currentCPUHandler = object : Handler(mainLooper) {
            override fun handleMessage(msg: Message) {
                if (msg.obj == null) return
                if (msg.what == 1) {
                    val msgData = msg.obj as String
                    try {
                        if (isDebug) Log.d(logTag, "msgData = $msgData")
                        val parts = msgData.split(";".toRegex()).toTypedArray()
                        cpuTemperature = parts[0]
                        val cpuParts = parts[1].split("\\|".toRegex()).toTypedArray()
                        for (i in cpuParts.indices) {
                            val cpuInfo = cpuParts[i]
                            val cpuInfoParts = cpuInfo.split(":".toRegex()).toTypedArray()
                            if (cpuInfoParts.size == 3) {
                                currentFreq!![i] = cpuInfoParts[1]
                                currentGov!![i] = cpuInfoParts[2]
                            } else {
                                currentFreq!![i] = "0"
                                currentGov!![i] = ""
                            }
                        }
                        dataAvailable = true
                        updateDisplay()
                    } catch (ex: ArrayIndexOutOfBoundsException) {
                        Log.e(logTag, "illegal data $msgData")
                    }
                }
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            with(currentCPUHandler) { removeMessages(1) }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(
                resolveSize(neededWidth, widthMeasureSpec),
                resolveSize(neededHeight, heightMeasureSpec)
            )
        }

        private fun getCPUInfoString(i: Int): String {
            val cpu = cpu!![i]
            val freq = currentFreq!![i]
            val gov = currentGov!![i]
            return "cpu$cpu: $gov ${String.format("%8s", toMHz(freq.toString()))}"
        }

        private fun getCpuTemp(cpuTemp: String): String {
            return when {
                cpuTempDivider > 1 -> with(String) { format("%s", cpuTemp.toInt() / cpuTempDivider) }
                else               -> cpuTemp
            }
        }

        public override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!dataAvailable) {
                return
            }
            val w = neededWidth
            val right = width - 1
            val x = right - mPaddingRight
            val top = mPaddingTop + 2
            val bottom = mPaddingTop + fh - 2
            var y = mPaddingTop - ascent.toInt()
            when {
                cpuTemperature != "0" -> {
                    with(canvas) {
                        drawText(
                            "Temp: ${getCpuTemp(cpuTemperature.toString())}°C",
                            (right - mPaddingRight - maxWidth).toFloat(), (y - 1).toFloat(), onlinePaint
                        )
                    }
                    y = y.plus(fh)
                }
            }
            for (i in currentFreq!!.indices) {
                val freq = currentFreq!![i]
                if (freq != "0") {
                    with(canvas) {
                        drawText(
                            getCPUInfoString(i),
                            (right - mPaddingRight - maxWidth).toFloat(), (y - 1).toFloat(), onlinePaint
                        )
                    }
                } else {
                    with(canvas) {
                        drawText(
                            "cpu${cpu!![i]}: offline", (right - mPaddingRight - maxWidth).toFloat(),
                            (y - 1).toFloat(), offlinePaint
                        )
                    }
                }
                y = y.plus(fh)
            }
        }

        fun updateDisplay() {
            if (!dataAvailable) {
                return
            }
            val nw = numberOfCpus
            val neededW: Int = mPaddingLeft + mPaddingRight + maxWidth
            val neededH: Int =
                mPaddingTop + mPaddingBottom + fh * ((if (cpuTempAvailable) 1 else 0) + nw)
            if (neededW != neededWidth || neededH != neededHeight) {
                neededWidth = neededW
                neededHeight = neededH
                requestLayout()
            } else {
                invalidate()
            }
        }

        private fun toMHz(mhz: String): String {
            return StringBuilder().append(Integer.valueOf(mhz) / 1000)
                .append(" MHz").toString()
        }

        override fun getHandler(): Handler {
            return currentCPUHandler
        }

        init {
            val density = context.resources.displayMetrics.density
            val paddingPx = (5 * density).roundToInt()
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            setBackgroundColor(Color.argb(0x60, 0, 0, 0))
            val textSize = textHeight
            val typeface = Typeface.create("monospace", Typeface.NORMAL)
            onlinePaint = Paint()
            onlinePaint.typeface = typeface
            onlinePaint.isAntiAlias = true
            onlinePaint.textSize = textSize.toFloat()
            onlinePaint.color = Color.WHITE
            onlinePaint.setShadowLayer(5.0f, 0.0f, 0.0f, Color.BLACK)
            offlinePaint = Paint()
            offlinePaint.typeface = typeface
            offlinePaint.isAntiAlias = true
            offlinePaint.textSize = textSize.toFloat()
            offlinePaint.color = Color.RED
            ascent = onlinePaint.ascent()
            val descent = onlinePaint.descent()
            fh = (descent - ascent + .5f).toInt()
            val maxWidthStr = "cpuX: interactive 00000000"
            maxWidth = onlinePaint.measureText(maxWidthStr).toInt()
            updateDisplay()
        }
    }

    protected inner class CurCPUThread(
        private val handler: Handler,
        numCpus: Int
    ) : Thread() {
        private var interrupt = false
        override fun interrupt() {
            interrupt = true
        }

        override fun run() {
            try {
                while (!interrupt) {
                    sleep(500)
                    val sb = StringBuffer()
                    val cpuTemp = readOneLine(cpuTempSensor)
                    sb.append(cpuTemp ?: "0")
                    sb.append(";")
                    for (i in 0 until numberOfCpus) {
                        val currCpu = cpu!![i]
                        val freqFile = cpuRoot + cpu!![i] + cpuCurrentTail
                        var currFreq = readOneLine(freqFile)
                        val govFile = cpuRoot + cpu!![i] + cpuGovernorTail
                        var currGov = readOneLine(govFile)
                        if (currFreq == null) {
                            currFreq = "0"
                            currGov = ""
                        }
                        sb.append("$currCpu:$currFreq:$currGov|")
                    }
                    sb.deleteCharAt(sb.length - 1)
                    with(handler) {
                        sendMessage(obtainMessage(1, sb.toString()))
                    }
                }
            } catch (ex: InterruptedException) {
                return
            }
        }

        init {
            numberOfCpus = numCpus
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(logTag, "CPUInfoService onCreate")
        cpuTempDivider = resources.getInteger(R.integer.config_cpuTempDivider)
        cpuTempSensor = resources.getString(R.string.config_cpuTempSensor)
        displayCpus = resources.getString(R.string.config_displayCpus)
        textHeight = resources.getDimensionPixelSize(R.dimen.cpu_info_text_height)
        numberOfCpus = getCpus(displayCpus)
        currentFreq = arrayOfNulls(numberOfCpus)
        currentGov = arrayOfNulls(numberOfCpus)
        cpuTempAvailable = readOneLine(cpuTempSensor) != null
        view = CPUView(this)
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.RIGHT or Gravity.TOP
        layoutParams.title = "CPU Info"
        startThread()
        dreamManager = IDreamManager.Stub.asInterface(
            checkService(DreamService.DREAM_SERVICE)
        )
        IntentFilter(Intent.ACTION_SCREEN_ON).apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            registerReceiver(screenStateReceiver, this)
        }
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        with(windowManager) {
            addView(view, layoutParams)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(logTag, "CPUInfoService onDestroy")
        stopThread()
        (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view)
        view = null
        unregisterReceiver(screenStateReceiver)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun getCpus(displayCpus: String?): Int {
        var numOfCpu = 1
        val cpuList: Array<String>?
        when {
            displayCpus != null -> {
                cpuList = displayCpus.split(",".toRegex()).toTypedArray()
                when {
                    cpuList.isNotEmpty() -> {
                        numOfCpu = cpuList.size
                        cpu = arrayOfNulls(numOfCpu)
                        for (i in 0 until numOfCpu) {
                            try {
                                cpuList[i].toInt()
                                cpu!![i] = cpuList[i]
                            } catch (ex: NumberFormatException) {
                                return getCpus(null)
                            }
                        }
                    }
                    else                 -> {
                        return getCpus(null)
                    }
                }
            }
            else                -> {
                // empty overlay, take all cores
                val numOfCpus = readOneLine(numberOfCpusPath)
                cpuList = numOfCpus!!.split("-".toRegex()).toTypedArray()
                when {
                    cpuList.size > 1 -> {
                        try {
                            val cpuStart = cpuList[0].toInt()
                            val cpuEnd = cpuList[1].toInt()
                            numOfCpu = cpuEnd - cpuStart + 1
                            if (numOfCpu < 0) numOfCpu = 1
                        } catch (ex: NumberFormatException) {
                            numOfCpu = 1
                        }
                    }
                }
                cpu = arrayOfNulls(numOfCpu)
                for (i in 0 until numOfCpu) {
                    cpu!![i] = i.toString()
                }
            }
        }
        return numOfCpu
    }

    private val screenStateReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON  -> {
                        if (isDebug) Log.d(logTag, "ACTION_SCREEN_ON $isDozeMode")
                        when {
                            !isDozeMode -> {
                                startThread()
                                view!!.visibility = View.VISIBLE
                            }
                        }
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        if (isDebug) Log.d(logTag, "ACTION_SCREEN_OFF")
                        view!!.visibility = View.GONE
                        stopThread()
                    }
                }
            }
        }
    private val isDozeMode: Boolean
        get() {
            try {
                if (dreamManager != null) {
                    if (dreamManager!!.isDozing) {
                        return true
                    }
                }
            } catch (ex: RemoteException) {
                return false
            }
            return false
        }

    private fun startThread() {
        if (isDebug) Log.d(logTag, "started CurCPUThread")
        currentCPUThread = CurCPUThread(view!!.handler, numberOfCpus)
        (currentCPUThread as CurCPUThread).start()
    }

    private fun stopThread() {
        when {
            currentCPUThread != null && currentCPUThread!!.isAlive -> {
                if (isDebug) Log.d(logTag, "stopping CurCPUThread")
                currentCPUThread!!.interrupt()
                try {
                    currentCPUThread!!.join()
                } catch (ex: InterruptedException) {
                }
            }
        }
        currentCPUThread = null
    }
}
