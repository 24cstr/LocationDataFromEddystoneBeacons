package com.example.beacondata

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.RemoteException
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.lemmingapex.trilateration.LinearLeastSquaresSolver

import com.lemmingapex.trilateration.NonLinearLeastSquaresSolver
import com.lemmingapex.trilateration.TrilaterationFunction
import org.altbeacon.beacon.*
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import kotlin.collections.ArrayList


class Proximity: BeaconConsumer,MonitorNotifier, RangeNotifier{

    //data stored in fiirestore
    data class DataPoint(var beaconID: String, var time: Timestamp, var distance:Double)

    //data recieved from Proximity class
    data class ProximityData(var beaconID:String, var distance: Double)

    //data stored in firebase RDBMS
    data class Trilat(var time:String, var cordinates:List<Double>)


    var db= FirebaseFirestore.getInstance()
    var db1=FirebaseDatabase.getInstance().reference

    val setting= FirebaseFirestoreSettings.Builder().setPersistenceEnabled(true).build()

    override fun getApplicationContext(): Context {
        return MainActivity.context
    }

    companion object {
        private val TAG = "Proximity"
        var list=ArrayList<ProximityData>()
    }

    override fun unbindService(p0: ServiceConnection?) {
        MainActivity.beaconManager.unbind(this)
        Log.d(TAG,"Unbinding Beacon Consumer")
    }

    override fun bindService(p0: Intent?, p1: ServiceConnection?, p2: Int): Boolean {

        Log.d(TAG,"Binding Beacon Consumer")
        return true
    }


    override fun onBeaconServiceConnect(){
        // Set the two identifiers below to null to detect any beacon regardless of identifiers


        var region = Region("nearby-Region",null,null, null)
        MainActivity.beaconManager.addMonitorNotifier(this)
        try {
            MainActivity.beaconManager.startMonitoringBeaconsInRegion(region)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }

        Log.d(
            TAG, "Looking for beacons in region"
        )

    }

    override fun didDetermineStateForRegion(p0: Int,p1:Region) {
        Log.d(TAG, "Saw a beacon $p0")
        MainActivity.beaconManager.startRangingBeaconsInRegion(p1)
        MainActivity.beaconManager.addRangeNotifier(this)

    }

    override fun didEnterRegion(p0: Region?) {
        Log.d(TAG,"Look am in region ")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun didRangeBeaconsInRegion(beacons: Collection<Beacon>, p1: Region) {

        list.clear()

        Log.d(TAG, "Inside ranging")
            for (beacon in beacons) {
                var d=ProximityData(beacon.toString(),beacon.distance)
                list.add(d)
                Log.d(TAG, "I see a beacon " + beacon.toString() + " that is less than " + beacon.distance + " meters away.")
            }
        updatedata()
    }

    override fun didExitRegion(p0: Region?) {

    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun updatedata(){

        if (MainActivity.loggedUser == "null") {
            Log.d("DataSync", "User is null" )
        }

        else {

            Log.d("DS","Got list")

            var positions= arrayOfNulls<DoubleArray>(list.size)
            var distances=DoubleArray(list.size)
            var i=0
            list.forEach {
                Log.d("Values","ID:"+it.beaconID+" Dist:"+it.distance)

                //trial Code for Trilatereation
                positions[i] = MainActivity.beaconLocation.getValue(it.beaconID)
                distances[i]= it.distance
                //end of trail code
                i++
                val newEntry = DataPoint(it.beaconID, Timestamp(System.currentTimeMillis()), it.distance)
                db.collection("USERS").document(MainActivity.loggedUser).collection("Beacons Seen").add(newEntry)
            }

            //trial Code for Trilateration
            if(list.size>2) {
                Log.d("Values", "Postions : ${positions.contentDeepToString()}  \n Distances : ${distances.contentToString()}")
                val solver = NonLinearLeastSquaresSolver(
                    TrilaterationFunction(positions, distances),
                    LevenbergMarquardtOptimizer()
                )
                val optimum = solver.solve()

                // the answer
                val centroid = optimum.point.toArray()


                // error and geometry information; may throw SingularMatrixException depending the threshold argument provided
                //val standardDeviation = optimum.getSigma(0.0)
                // val covarianceMatrix = optimum.getCovariances(0.0)

                Log.d("Trilat", "The mobile is at : "+centroid[0] + " , "+centroid[1])

                //end of trilat calc

                //update to Firebase RDBMS

                val current = LocalDateTime.now()
                val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                val formatted = current.format(formatter)

                //val t = Trilat(formatted, centroid.asList())
                db1.child("Locations").child(MainActivity.loggedUser).child(formatted).setValue(centroid.asList())

            }

        }
    }
}