package com.example.android.budnypredkosciomierz

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibrationEffect.DEFAULT_AMPLITUDE
import android.os.Vibrator
import android.text.InputFilter
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.databinding.DataBindingUtil
import com.example.android.budnypredkosciomierz.databinding.ActivityMainBinding
import java.util.*

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationManager: LocationManager
    private var lastLocation: Location? = null
    private var lastLocationTime: Long? = null
    private var listeningStatus: Int = 0
    private var bufferMin: Long = 0
    private var bufferMax: Long = 0

    private var currentToast: Toast? = null
    private var lastAlertTime: Long = 0L

    private fun showExclusiveToast(message: String) {
        currentToast?.cancel()
        currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        currentToast?.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        binding.speedTextView.text = getString(R.string.default_speed)

        setDecimalLimit(binding.minSpeedEdit)
        setDecimalLimit(binding.maxSpeedEdit)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        binding.doneButton.setOnClickListener {
            addSpeeds(it)
        }

        binding.editButton.setOnClickListener {
            editSpeeds()
        }

        binding.startButton.setOnClickListener {
            startTracking()
        }

        binding.pauseButton.setOnClickListener {
            stopTracking()
        }
    }

    private fun stopTracking() {
        binding.pauseButton.visibility = View.GONE

        binding.startButton.visibility = View.VISIBLE
        binding.editButton.visibility = View.VISIBLE

        currentToast?.cancel()

        locationManager.removeUpdates(this)
        listeningStatus = 0

        binding.speedTextView.text = getString(R.string.default_speed)
    }

    private fun startTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            binding.startButton.visibility = View.GONE
            binding.editButton.visibility = View.GONE

            binding.pauseButton.visibility = View.VISIBLE

            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, this)
            bufferMin = 0L
            bufferMax = 0L
            listeningStatus = 1
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }

    private fun editSpeeds() {
        binding.minSpeedEdit.visibility = View.VISIBLE
        binding.maxSpeedEdit.visibility = View.VISIBLE
        binding.doneButton.visibility = View.VISIBLE

        binding.minSpeedText.visibility = View.GONE
        binding.maxSpeedText.visibility = View.GONE
        binding.minTextView.visibility = View.GONE
        binding.maxTextView.visibility = View.GONE
        binding.editButton.visibility = View.GONE
        binding.startButton.visibility = View.GONE
    }

    private fun addSpeeds(view: View) {
        if(binding.minSpeedEdit.text.isNotEmpty() && binding.maxSpeedEdit.text.isNotEmpty()) {
            if (binding.minSpeedEdit.text.toString().toDouble() <= binding.maxSpeedEdit.text.toString().toDouble()) {
                    val builder = StringBuilder()

                    builder.append(binding.minSpeedEdit.text).append(" km/h")
                    binding.minSpeedText.text = builder

                    builder.clear()

                    builder.append(binding.maxSpeedEdit.text).append(" km/h")
                    binding.maxSpeedText.text = builder

                    binding.minSpeedEdit.visibility = View.GONE
                    binding.maxSpeedEdit.visibility = View.GONE
                    binding.doneButton.visibility = View.GONE

                    binding.minSpeedText.visibility = View.VISIBLE
                    binding.maxSpeedText.visibility = View.VISIBLE
                    binding.minTextView.visibility = View.VISIBLE
                    binding.maxTextView.visibility = View.VISIBLE
                    binding.editButton.visibility = View.VISIBLE
                    binding.startButton.visibility = View.VISIBLE

                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
            } else {
                val toast = Toast.makeText(this, getString(R.string.error_min_greater), Toast.LENGTH_LONG)
                toast.show()
            }
        } else {
            val toast = Toast.makeText(this, getString(R.string.error_speed_empty), Toast.LENGTH_LONG)
            toast.show()
        }


    }

    private fun setDecimalLimit(editText: EditText) {
        val inputFilter = InputFilter { source, start, end, dest, dstart, dend ->
            val builder = StringBuilder(dest)
            builder.replace(dstart, dend, source.subSequence(start, end).toString())

            val text = builder.toString()
            if (text.isNotEmpty() && text != ".") {
                try {
                    val decimalIndex = text.indexOf('.')
                    if (decimalIndex != -1 && text.length - decimalIndex - 1 > 2) {
                        ""
                    } else {
                        null
                    }
                } catch (e: NumberFormatException) {
                    ""
                }
            } else {
                null
            }
        }

        val filters = arrayOf(inputFilter)
        editText.filters = filters
    }

    override fun onResume() {
        super.onResume()
        if(listeningStatus == 2) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, this)
                bufferMin = 0L
                bufferMax = 0L
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            }
        }

    }

    override fun onPause() {
        super.onPause()
        currentToast?.cancel()
        if(listeningStatus == 1) {
            locationManager.removeUpdates(this)
            listeningStatus = 2
        }
    }

    override fun onLocationChanged(location: Location) {
        val currentTime = System.currentTimeMillis()

        if (lastLocation == null || (lastLocationTime != null && (currentTime - lastLocationTime!!) > 3000L)) {
            lastLocation = location
            lastLocationTime = currentTime
            bufferMin = 0L
            bufferMax = 0L
            return
        }

        val dystans = lastLocation!!.distanceTo(location)
        val czas = (currentTime - lastLocationTime!!) / 1000.0f

        if (dystans > 0 && czas > 0) {
            val predkosc = if (location.hasSpeed() && location.speed > 0f) {
                location.speed * 3.6
            } else {
                (dystans / czas) * 3.6
            }

            if (predkosc > 50.0 || dystans > 50.0) {
                lastLocation = location
                lastLocationTime = currentTime
                bufferMin = 0L
                bufferMax = 0L
                return
            }

            binding.speedTextView.text = String.format(Locale.getDefault(), "%.2f km/h", predkosc)

            val minSpeed = binding.minSpeedEdit.text.toString().toDoubleOrNull() ?: 0.0
            val maxSpeed = binding.maxSpeedEdit.text.toString().toDoubleOrNull() ?: 0.0

            if (predkosc < minSpeed) {
                if (bufferMin == 0L) {
                    bufferMin = currentTime
                } else if (currentTime - bufferMin > 3000L) {
                    if (currentTime - lastAlertTime > 2000L) {
                        ToneGenerator(AudioManager.STREAM_ALARM, 100)
                            .startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
                        showExclusiveToast(getString(R.string.alert_speed_too_slow))
                        lastAlertTime = currentTime
                    }
                }
            } else {
                bufferMin = 0L
            }

            if (predkosc > maxSpeed) {
                if (bufferMax == 0L) {
                    bufferMax = currentTime
                } else if (currentTime - bufferMax > 3000L) {
                    if (currentTime - lastAlertTime > 2000L) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            getSystemService<Vibrator>()?.vibrate(
                                VibrationEffect.createOneShot(200, DEFAULT_AMPLITUDE)
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            getSystemService<Vibrator>()?.vibrate(200)
                        }
                        showExclusiveToast(getString(R.string.alert_speed_too_fast))
                        lastAlertTime = currentTime
                    }
                }
            } else {
                bufferMax = 0L
            }

            if (predkosc in minSpeed..maxSpeed) {
                currentToast?.cancel()
            }

            lastLocation = location
            lastLocationTime = currentTime

        } else if (czas > 1) {
            binding.speedTextView.text = getString(R.string.default_speed)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 1 || grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            val toast = Toast.makeText(this, getString(R.string.error_permission), Toast.LENGTH_LONG)
            toast.show()
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {

    }
}
