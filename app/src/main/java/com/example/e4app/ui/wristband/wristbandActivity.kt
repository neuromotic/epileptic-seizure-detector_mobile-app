package com.example.e4app.ui.wristband

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.empatica.empalink.ConnectionNotAllowedException
import com.empatica.empalink.EmpaDeviceManager
import com.empatica.empalink.EmpaticaDevice
import com.empatica.empalink.config.EmpaSensorType
import com.empatica.empalink.config.EmpaStatus
import com.empatica.empalink.delegate.EmpaDataDelegate
import com.empatica.empalink.delegate.EmpaStatusDelegate
import com.example.e4app.R
import com.example.e4app.models.Data
import com.example.e4app.models.Datos
import com.example.e4app.models.Respuesta
import com.example.e4app.services.DatosService
import com.example.e4app.services.ServiceBuilder
import com.jakewharton.threetenabp.AndroidThreeTen
import kotlinx.android.synthetic.main.activity_principal.*
import kotlinx.android.synthetic.main.activity_timer.*
import org.jetbrains.anko.toast
import org.threeten.bp.LocalDateTime
import retrofit2.Call
import java.io.File
import java.io.FileOutputStream
import java.math.RoundingMode
import java.text.DecimalFormat

import retrofit2.Callback
import retrofit2.Response

private const val PERMISSION_REQUEST = 10

open class wristbandActivity : AppCompatActivity(), EmpaDataDelegate, EmpaStatusDelegate {

    private var permissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.INTERNET)

    private val REQUEST_ENABLE_BT = 1
    private val REQUEST_PERMISSION_ACCESS_COARSE_LOCATION = 1
    private val EMPATICA_API_KEY = "d9bf49c7aea04d00a3af84c596139c4e"
    private var deviceManager: EmpaDeviceManager? = null

    private var flagConnected = false
    private var flagInitTimer = false
    private var flagFinishTimer = false

    internal var dataEDAsensor = ""
    internal var dataACCsensor = ""
    internal var dataBVPsensor = ""

    private var secondsRemaining = 10

    val timer = Counter(secondsRemaining.toLong()*1000, 1000)
    var name = ""
    var comment = ""
    var fecha = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidThreeTen.init(this)
        name = intent.getStringExtra("Name")
        comment = intent.getStringExtra("Comment")

        println(name)
        println(comment)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_principal)

        requestPermissions(permissions, PERMISSION_REQUEST)

        textView4.typeface = Typeface.createFromAsset(assets,"fonts/Font.otf")
        textView6.typeface = Typeface.createFromAsset(assets, "fonts/Font.otf")

    }

    @SuppressLint("RestrictedApi")
    override fun onResume() {
        super.onResume()
        btnScan.setOnClickListener {
            toast("Se inicia la búsqueda")
            initEmpaticaDeviceManager()
        }

        btnPrueba.setOnClickListener {
            postData()
            println("name " + name)
            println("comment " + comment)


        }

        btnStartCount.setOnClickListener {

            if (flagConnected){
                fecha = LocalDateTime.now().toString()
                val dialogInit = AlertDialog.Builder(this, R.style.InitDialog)
                dialogInit.setMessage("Antes de iniciar la captura de datos, asegúrese de que la pulsera esté bien ajustada a su brazo.")
                        .setTitle("ATENCIÓN")
                        .setPositiveButton("Iniciar", DialogInterface.OnClickListener{dialog, id ->
                            secondsRemaining = 10
                            flagInitTimer = true
                            timer.start()
                            println("Inicia temporizador")
                            progress_countdown.max = secondsRemaining
                            mainInvisible()

                        })
                        .setNegativeButton("Cancelar", DialogInterface.OnClickListener{dialog, id ->
                            dialog.cancel()
                        })
                val alertInit = dialogInit.create()
                alertInit.show()


            }else{
                toast("No está conectado a ningún dispositivo")
            }
        }

        btnStop.setOnClickListener {

            val dialogStop = AlertDialog.Builder(this, R.style.StopDialog)
            dialogStop.setMessage("La captura de datos se detendrá. ¿Está de acuerdo?")
                    .setCancelable(false)
                    .setPositiveButton("Si",DialogInterface.OnClickListener{dialog, id ->
                        toast("Detenido")
                        timer.cancel()
                        timerInvisible()
                        progress_countdown.progress = 0
                        secondsRemaining = 10
                        //flagConnected = false
                        flagFinishTimer = false
                        dataEDAsensor = ""
                        dataACCsensor = ""
                        dataBVPsensor = ""
                    })
                    .setNegativeButton("No", DialogInterface.OnClickListener {
                        dialog, id -> dialog.cancel()
                    })
            val alertStop = dialogStop.create()
            alertStop.show()
        }

    }

    private fun postData(){
        val newRespuesta = Respuesta()
        val newData = Data()

        newData.name = name
        newData.comment = comment
        newData.date = fecha.toString()

        println(newData.name)
        println(newData.comment)
        println(newData.date)

        val DatosService = ServiceBuilder.buildService(DatosService::class.java)
        val requestCall = DatosService.uploadData(newData)

        requestCall.enqueue(object : Callback<Datos> {

            override fun onResponse(call: Call<Datos>, response: Response<Datos>) {
                if(response.isSuccessful){
                    println("SUCCESSFULL")
                    println(response.body()!!.response)

                }else{
                    println("FAIL ON RESPONSE")
                }
            }
            override fun onFailure(call: Call<Datos>, t: Throwable) {

            }
        })
    }

    inner class Counter(millisInFuture: Long, countDownTimer: Long) : CountDownTimer(millisInFuture, countDownTimer){
        override fun onFinish() {
            toast("Tiempo finalizado")
            println("Tiempo finalizado")
            progress_countdown.progress = 0
            var dataSensors = dataEDAsensor + dataACCsensor + dataBVPsensor

            val name = "ARCHIVO.csv"
            val textFile = File(Environment.getExternalStorageDirectory(),name)
            val fos = FileOutputStream(textFile)
            fos.write(dataSensors.toByteArray())
            fos.close()
            dataEDAsensor = ""
            dataACCsensor = ""
            dataBVPsensor = ""

            toast("Archivo guardado")

            postData()

            timerInvisible()
            deviceManager!!.stopScanning()
            deviceManager!!.cleanUp()
            flagConnected = false

            btnScan!!.setEnabled(true)
            updateLabel(txvStatus, "DESCONECTADO")
        }

        override fun onTick(millisUntilFinished: Long) {
            secondsRemaining = millisUntilFinished.toInt() / 1000
            println("Timer: " + millisUntilFinished/ 1000)

            updateCountdownUI()
            progress_countdown.progress = (secondsRemaining).toInt()

        }
    }

    private fun updateCountdownUI(){
        val minutesUntilFinished = secondsRemaining / 60
        val secondsInMinutesUntilFinifhed = secondsRemaining - minutesUntilFinished * 60
        val secondsStr = secondsInMinutesUntilFinifhed.toString()
        textViewTimer.text = "$minutesUntilFinished:${
        if (secondsStr.length == 2) secondsStr
        else "0" + secondsStr}"
    }

    private fun initEmpaticaDeviceManager() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_PERMISSION_ACCESS_COARSE_LOCATION)
        } else {

            if (TextUtils.isEmpty(EMPATICA_API_KEY)) {
                AlertDialog.Builder(this)
                        .setTitle("Warning")
                        .setMessage("Please insert your API KEY")
                        .setNegativeButton("Close") { dialog, which ->
                            finish()
                        }
                        .show()
                return
            }

            deviceManager = EmpaDeviceManager(applicationContext, this, this)

            deviceManager!!.authenticateWithAPIKey(EMPATICA_API_KEY)
        }
    }

    override fun onPause() {
        super.onPause()
        if (deviceManager != null) {
            deviceManager!!.stopScanning()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (deviceManager != null) {
            deviceManager!!.cleanUp()
        }
    }


    override fun didDiscoverDevice(device: EmpaticaDevice?, deviceLabel: String?, rssi: Int, allowed: Boolean) {
        Log.i("allowed2", allowed.toString())
        if (allowed) {
            deviceManager!!.stopScanning()
            try {
                deviceManager!!.connectDevice(device)
                if (deviceLabel != null) {
                    updateLabel(txvStatus, "ENLAZANDOSE A: " + deviceLabel)
                }
            } catch (e: ConnectionNotAllowedException) {
                toast("Sorry, you can't connect to this device")
            }

        }

    }

    override fun didRequestEnableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun didUpdateSensorStatus(status: Int, type: EmpaSensorType?) {
        didUpdateOnWristStatus(status)

    }

    override fun didUpdateStatus(status: EmpaStatus?) {

        if(status!!.name == "DISCONNECTED"){
            updateLabel(txvStatus, "DESCONECTADO")
            btnScan!!.setEnabled(true)
        }else if (status!!.name == "CONNECTED"){
            updateLabel(txvStatus, "ENLAZADO")
            btnScan!!.setEnabled(false)
        }else{
            updateLabel(txvStatus, "LISTO PARA ENLAZAR" + '\n' + "Activa la pulsera y espera unos segundos")
            btnScan!!.setEnabled(false)
        }

        if (status == EmpaStatus.READY) {
            updateLabel(txvStatus, "LISTO PARA ENLAZAR" + '\n' + "Activa la pulsera y espera unos segundos")
            deviceManager!!.startScanning()

        } else if (status == EmpaStatus.CONNECTED) {
            flagConnected = status.toString() == getString(R.string.connected)

        } else if (status == EmpaStatus.DISCONNECTED) {
        }
    }


    private fun updateLabel(label: TextView, text: String) {
        runOnUiThread { label.text = text }
    }

    override fun didReceiveAcceleration(x: Int, y: Int, z: Int, timestamp: Double) {
        if(flagInitTimer){
            var auxACC = ""
            auxACC += x.toString() + "," + y.toString() + "," + z.toString()
            dataACCsensor += auxACC
            dataACCsensor += "\n"
        }
    }

    override fun didReceiveBVP(bvp: Float, timestamp: Double) {
        println("INICIADO SENSOR BVP" + bvp.toString())
        // Log.i("sensorToma", bvp.toString())
        if(flagInitTimer){
            val df = DecimalFormat("#.######")
            df.roundingMode = RoundingMode.CEILING
            dataBVPsensor += df.format(bvp).toString()
            dataBVPsensor += "\n"
        }
    }

    override fun didReceiveBatteryLevel(level: Float, timestamp: Double) {

    }

    override fun didReceiveGSR(gsr: Float, timestamp: Double) {
        if(flagInitTimer){
            val df = DecimalFormat("#.######")
            df.roundingMode = RoundingMode.CEILING
            dataEDAsensor += df.format(gsr).toString()
            dataEDAsensor += "\n"
        }
    }

    override fun didReceiveIBI(ibi: Float, timestamp: Double) {

    }

    override fun didReceiveTemperature(t: Float, timestamp: Double) {

    }

    override fun didReceiveTag(timestamp: Double) {

    }

    override fun didEstablishConnection() {

    }

    override fun didUpdateOnWristStatus(status: Int) {

    }

    @SuppressLint("RestrictedApi")
    private fun timerInvisible(){
        textView4.setVisibility(TextView.VISIBLE)
        btnScan.setVisibility(Button.VISIBLE)
        txvStatus.setVisibility(TextView.VISIBLE)
        btnStartCount.setVisibility(Button.VISIBLE)
        textViewTimer.setVisibility(TextView.INVISIBLE)
        textView6.setVisibility(TextView.INVISIBLE)
        btnStop.setVisibility(Button.INVISIBLE)
    }

    @SuppressLint("RestrictedApi")
    private fun mainInvisible(){
        textView4.setVisibility(TextView.INVISIBLE)
        btnScan.setVisibility(Button.INVISIBLE)
        txvStatus.setVisibility(TextView.INVISIBLE)
        btnStartCount.setVisibility(Button.INVISIBLE)
        textViewTimer.setVisibility(TextView.VISIBLE)
        textView6.setVisibility(TextView.VISIBLE)
        btnStop.setVisibility(Button.VISIBLE)
    }

}