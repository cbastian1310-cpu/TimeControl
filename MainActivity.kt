
package com.timecontrol.app

import android.app.Activity
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.media.AudioManager
import android.media.ToneGenerator
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.text.InputType

class MainActivity : Activity() {

    private lateinit var display: TextView
    private lateinit var stage: TextView
    private lateinit var workInput: EditText
    private lateinit var restInput: EditText

    private var timer: CountDownTimer? = null
    private var remaining = 0L
    private var running = false
    private var isWork = true
    private var warned = false

    private var tone: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    private val alarmRunnable = object : Runnable {
        override fun run() {
            tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
            handler.postDelayed(this, 800)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(17, 24, 39))
        }

        fun addText(
            text: String,
            size: Float,
            color: Int,
            bold: Boolean = false
        ): TextView {
            return TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                if (bold) setTypeface(null, Typeface.BOLD)
                setPadding(dp(4), dp(8), dp(4), dp(8))
                root.addView(this)
            }
        }

        addText("TIME CONTROL", 26f, Color.WHITE, true)

        stage = addText("PERÍODO", 18f, Color.rgb(96, 165, 250), true)

        display = addText("00:00", 68f, Color.WHITE, true)

        addText("Duración del período (minutos)", 15f, Color.LTGRAY)

        workInput = EditText(this).apply {
            setText("20")
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.rgb(55, 65, 81))
            root.addView(this, LinearLayout.LayoutParams(
                -1, dp(52)
            ))
        }

        addText("Duración del descanso (minutos)", 15f, Color.LTGRAY)

        restInput = EditText(this).apply {
            setText("5")
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(55, 65, 81))
            root.addView(this, LinearLayout.LayoutParams(
                -1, dp(52)
            ))
        }

        val startButton = Button(this).apply {
            text = "INICIAR"
            setOnClickListener {
                if (!running) {
                    if (remaining <= 0L) {
                        isWork = true
                        remaining = duration(true)
                    }
                    stopAlarm()
                    startTimer()
                }
            }
        }

        val pauseButton = Button(this).apply {
            text = "PAUSAR"
            setOnClickListener {
                if (running) {
                    timer?.cancel()
                    timer = null
                    running = false
                }
            }
        }

        val resetButton = Button(this).apply {
            text = "REINICIAR"
            setOnClickListener {
                timer?.cancel()
                timer = null
                running = false
                stopAlarm()
                isWork = true
                warned = false
                remaining = 0L
                display.text = "00:00"
                stage.text = "PERÍODO"
            }
        }

        fun addButton(button: Button) {
            root.addView(button, LinearLayout.LayoutParams(
                -1, dp(54)
            ).apply {
                topMargin = dp(8)
            })
        }

        addButton(startButton)
        addButton(pauseButton)
        addButton(resetButton)

        addText(
            "Aviso previo: 10 segundos",
            13f,
            Color.LTGRAY
        )

        setContentView(root)
    }

    private fun duration(work: Boolean): Long {
        val input = if (work) workInput else restInput
        val minutes = input.text.toString().toLongOrNull() ?: 1L
        return minutes.coerceAtLeast(1L) * 60_000L
    }

    private fun startTimer() {
        if (remaining <= 0L) {
            remaining = duration(isWork)
        }

        running = true
        warned = false
        stage.text = if (isWork) "PERÍODO" else "DESCANSO"

        timer = object : CountDownTimer(remaining, 250) {

            override fun onTick(millisUntilFinished: Long) {
                remaining = millisUntilFinished

                val seconds = millisUntilFinished / 1000
                val minutesPart = seconds / 60
                val secondsPart = seconds % 60

                display.text = String.format(
                    "%02d:%02d",
                    minutesPart,
                    secondsPart
                )

                if (millisUntilFinished <= 10_000L && !warned) {
                    warned = true
                    try {
                        tone?.release()
                        tone = ToneGenerator(
                            AudioManager.STREAM_ALARM, 100
                        )
                        tone?.startTone(
                            ToneGenerator.TONE_PROP_BEEP2, 500
                        )
                    } catch (_: Exception) {
                    }
                }
            }

            
override fun onFinish() {
    remaining = 0L
    running = false
    display.text = "00:00"

    startAlarm()

    isWork = !isWork
    remaining = duration(isWork)

    handler.postDelayed({
        stopAlarm()
        startTimer()
    }, 2000)
}

        }.start()
    }

    private fun startAlarm() {
        try {
            tone?.release()
            tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)

            handler.removeCallbacks(alarmRunnable)
            handler.post(alarmRunnable)

            vibrator?.let {
                val pattern = longArrayOf(0, 400, 200, 400)

                if (Build.VERSION.SDK_INT >= 26) {
                    it.vibrate(
                        VibrationEffect.createWaveform(pattern, 0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(pattern, 0)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun stopAlarm() {
        handler.removeCallbacks(alarmRunnable)

        try {
            vibrator?.cancel()
            tone?.stopTone()
            tone?.release()
        } catch (_: Exception) {
        }

        tone = null
    }

    override fun onDestroy() {
        timer?.cancel()
        stopAlarm()
        super.onDestroy()
    }
}
