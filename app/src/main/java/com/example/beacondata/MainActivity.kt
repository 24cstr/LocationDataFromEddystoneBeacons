package com.example.beacondata

import android.Manifest.permission.*
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.android.synthetic.main.activity_main.*
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.service.RunningAverageRssiFilter

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth //var for accessing firebase authentication

    companion object {
        lateinit var loggedUser: String
        private const val TAG = "MainActivity"
        lateinit var context:Context
        lateinit var beaconManager: BeaconManager
        lateinit var beaconLocation:HashMap<String,DoubleArray>
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        //Set on Click Listener for Login and logout buttons
        var loginbut = findViewById<Button>(R.id.loginbut)
        loginbut.setOnClickListener { loginActivity() }

        var logoutbut = findViewById<Button>(R.id.logoutbutt)
        logoutbut.setOnClickListener { logoutActivity() }

        //Getting permissions for Location and Background location
        getLocationPermissions()
        context=this.applicationContext
        beaconManager= BeaconManager.getInstanceForApplication(context)
        beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_UID_LAYOUT))
        BeaconManager.setRssiFilterImplClass(RunningAverageRssiFilter::class.java)
        beaconManager.backgroundBetweenScanPeriod = 60000
        beaconManager.backgroundScanPeriod = 20000
        beaconManager.foregroundBetweenScanPeriod = 60000
        beaconManager.foregroundScanPeriod = 20000

        beaconLocation=HashMap()

    }

    override fun onStart() {
        super.onStart()

        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (mBluetoothAdapter == null) {
            Toast.makeText(applicationContext, "Bluetooth Not Supported", Toast.LENGTH_LONG).show()
        } else {
            // if bluetooth is supported but not enabled then enable it
            if (!mBluetoothAdapter.isEnabled) {
                val bluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                bluetoothIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(bluetoothIntent)
            }
        }

        val user=auth.currentUser
        updateUI(user)

        updateBeaconList()

    }

    private fun updateBeaconList() {

        //Function to populate beacons Id and the pos

        var c1=DoubleArray(2)
        var c2=DoubleArray(2)
        var c3=DoubleArray(2)
        var c4=DoubleArray(2)

        c1[0]=0.0;c1[1]=0.0
        beaconLocation["id1: 0xfffffaaaaaaaaaafffff id2: 0x00a0500632a9"] = c1

        c2[0]=0.0;c2[1]=5.5
        beaconLocation["id1: 0xfffffaaaaaaaaaafffff id2: 0x00a0500698e8"] = c2

        c3[0]=3.0;c3[1]=0.0
        beaconLocation["id1: 0xfffffaaaaaaaaaafffff id2: 0x00a0500636df"] = c3

        c4[0]=6.0;c4[1]=5.5
        beaconLocation["id1: 0xfffffaaaaaaaaaafffff id2: 0x00a05006971d"] = c4
    }

    //Updates UI before and after logging in
    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {

            //remove all login fields
            findViewById<EditText>(R.id.username).visibility=GONE
            findViewById<EditText>(R.id.password).visibility=GONE
            loginbut.visibility=GONE

            //Display logged in message and logout button
            loggedUser=user.email?.removeSuffix("@idrbt.ac.in").toString()
            findViewById<TextView>(R.id.header).text = "Login Successful. Welcome $loggedUser"
            logoutbutt.visibility= VISIBLE


        } else {

            //remove loggout button
            logoutbutt.visibility= GONE
            loggedUser="null"

            //Display all loggin options
            findViewById<TextView>(R.id.header).text="Login Page"
            findViewById<EditText>(R.id.username).visibility = VISIBLE
            findViewById<EditText>(R.id.password).visibility = VISIBLE
            loginbut.visibility = VISIBLE

        }
    }

    //Controls log in action
    private fun loginActivity() {

        //Get username and password from Textfields
        val uname = findViewById<EditText>(R.id.username).text.toString().trim()+"@idrbt.ac.in"
        val pass = findViewById<EditText>(R.id.password).text.toString()

        //Login
        auth.signInWithEmailAndPassword(uname, pass)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {

                    // Sign in success, update UI with the signed-in user's information
                    val user = auth.currentUser
                    updateUI(user)

                    //Start background recording service
                    beaconManager.bind(Proximity())
                }

                else {
                    // If sign in fails, display a message to the user.
                    Toast.makeText(baseContext, "Authentication failed.", Toast.LENGTH_SHORT).show()
                }


            }


    }

    //controls log out action
    private fun logoutActivity() {
        //signout user
        auth.signOut()
        updateUI(auth.currentUser)
        beaconManager.removeAllRangeNotifiers()
        beaconManager.removeAllMonitorNotifiers()

    }

    //Checks is permission are available and asks for permission not allowed yet
    private fun getLocationPermissions() {

        val hasForegroundLocationPermission = ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasForegroundLocationPermission) {
            val hasBackgroundLocationPermission = ActivityCompat.checkSelfPermission(this, ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

            if (hasBackgroundLocationPermission) {
                Log.d(TAG, "LOCATION PERMISSION Prese")
            }
            else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(ACCESS_BACKGROUND_LOCATION), 1
                )
            }
        }

        else {

            ActivityCompat.requestPermissions(this, arrayOf(ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION), 2)
        }
    }

}
