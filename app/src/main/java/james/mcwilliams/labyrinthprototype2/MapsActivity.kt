package james.mcwilliams.labyrinthprototype2

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import james.mcwilliams.labyrinthprototype2.databinding.ActivityMapsBinding
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private val coarseLocationCode: Int = 1
    private val fineLocationCode: Int = 2
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentlyPathing: Boolean = false
    private var currentlySearching: Boolean = false
    var locationRequest = LocationRequest.Builder(1000)
        .setIntervalMillis(1000)
        .setMaxUpdates(120)
        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        .build()
    lateinit var locationCallback: LocationCallback
    private var currentTreasurePath: TreasurePath = TreasurePath()


    private val apiService = createApiService()
    private var allPaths = arrayListOf<TreasurePath>()


    override fun onCreate(savedInstanceState: Bundle?) {
        if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestFineLocationPermission()
        }
        if (!checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            requestCoarseLocationPermission()
        }


        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                Log.d("PATH", "received locationCallback")
                if (currentTreasurePath != null) {
                    currentTreasurePath.addCoordinate(locationResult.locations)
                    if (currentlyPathing){
                        callNewPathPoint(currentTreasurePath.pathID,
                            currentTreasurePath.coordinates.size.toLong(),
                            locationResult.locations[0].latitude,
                            locationResult.locations[0].longitude)
                    }

                }
                if (currentlyPathing){
                    drawPath(currentTreasurePath.coordinates, Color.GREEN)
                } else {
                    drawPath(currentTreasurePath.coordinates, Color.YELLOW)
                }
            }
        }
        // TODO: connect to DB API here?
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            mMap.isMyLocationEnabled = true
        }

        googleMap.setMapStyle(
            MapStyleOptions.loadRawResourceStyle(this, R.raw.custom_map_style)
        )

        mMap.isIndoorEnabled = false
        mMap.uiSettings.isScrollGesturesEnabled = true //previously false
        //mMap.uiSettings.isMyLocationButtonEnabled = false
        mMap.uiSettings.isMapToolbarEnabled = false
        mMap.setMaxZoomPreference(20F)
        mMap.setMinZoomPreference(17.5F)


        // path tracing floating action button stuff
        val fab: View = findViewById(R.id.fab)
        fab.setOnClickListener { view ->
            // if you're not already pathing, you want to record a message before you start pathing
            if (!currentlyPathing) {
                showMessageInput(this, view)
            } else {
                // if you're already pathing, you want to stop pathing.
                startPathing(view, "")
            }


        }

        val fab2: View = findViewById(R.id.fab2)
        fab2.setOnClickListener { view ->
            startSearching(view)
        }
    }

    private fun checkPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestFineLocationPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), fineLocationCode)
    }

    private fun requestCoarseLocationPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), coarseLocationCode)
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == fineLocationCode || requestCode == coarseLocationCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val toast: Toast = Toast.makeText(this@MapsActivity,R.string.permissions_thanks, Toast.LENGTH_SHORT)
                toast.setGravity(Gravity.TOP, 0, 0)
                toast.show()
                mMap.isMyLocationEnabled = true
            }
        }
    }


    /**
     * If the user isn't already tracing a treasure path, then this will start one. If they are
     * already tracing a treasure path, then this stops it.
     * Does nothing if the user is already tracing a search path.
     */
    @SuppressLint("MissingPermission")
    private fun startPathing(view: View, userInput: String) {
        if (!currentlySearching) {
            var displayText = ""
            currentlyPathing = !currentlyPathing

            if (currentlyPathing) {
                currentTreasurePath.clear()
                currentTreasurePath.message = userInput

                callNewTreasurePath(currentTreasurePath.pathID,
                    currentTreasurePath.userID,
                    currentTreasurePath.message)

                displayText = resources.getString(R.string.pathing_text_on)
                // start placing nodes in the path, once per second
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

                showTopSnackbar(view, displayText, Snackbar.LENGTH_INDEFINITE)
            } else {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                displayText = resources.getString(R.string.pathing_text_off)
                showTopSnackbar(view, displayText, Snackbar.LENGTH_LONG)
            }
        }
    }

    /**
     * If the user isn't already tracing a search path, then this will retrieve paths from the
     * database and start tracing one. If they are already tracing a search path, then this stops
     * the path and checks it against the paths retrieved from the database.
     * Does nothing if the user is currently tracing a treasure path.
     */
    @SuppressLint("MissingPermission")
    private fun startSearching(view: View) {
        if (!currentlyPathing) {
            var displayText = ""
            currentlySearching = !currentlySearching

            if (currentlySearching) {
                currentTreasurePath.clear()

                // get paths from DB (updates allPaths variable)
                callGetPaths()

                displayText = resources.getString(R.string.searching_text_on)
                // start placing nodes in the path, once per second
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

                showTopSnackbar(view, displayText, Snackbar.LENGTH_INDEFINITE)
            } else {
                fusedLocationClient.removeLocationUpdates(locationCallback)

                displayText = resources.getString(R.string.pathing_text_off)
                showTopSnackbar(view, displayText, Snackbar.LENGTH_LONG)

                // check against paths from the DB
                Log.d("COMPAREPATH", "Comparing to saved paths")
                for (path in allPaths) {
                    Log.d("COMPAREPATH", "Comparing to path ID: " + path.pathID)
                    // need to fix coordinates here since they're still in the messed up format
                    path.pathPointsToCoordinates()
                    if (currentTreasurePath.compareToPath(path) > 0.8) {
                        if (path.compareToPath(currentTreasurePath) > 0.8) {
                            Log.d("COMPAREPATH", "Paths matched. ID: " + path.pathID)
                            val foundMessage = getString(R.string.found_message)
                            val message = path.message
                            showTopSnackbar(view,
                                "$foundMessage \n $message",
                            15000)
                        }
                    }
                }
            }
        }
    }

    /**
     * Draws a series of lines on the map, joining each location in the given list.
     */
    private fun drawPath(locations: List<LatLng>, color: Int): Boolean {
        Log.d("PATH", "drawPath: locations.size = " + locations.size)
        if (locations.size < 2) {
            return false
        }

        for ((index) in locations.withIndex()) {
            if (index != locations.size-1) {
                Log.d("PATH", "drawPathNode")
                drawPathNode(locations[index], locations[index+1], color)
            }
        }
        return true;
    }

    /**
     * Draws a line on the map from pos1 to pos2.
     */
    private fun drawPathNode(latLng1: LatLng, latLng2: LatLng, color: Int) {
        mMap.addPolyline(PolylineOptions()
            .add(latLng1, latLng2)
            .width(5F)
            .color(color))
    }

    fun createApiService(): ApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://jems.bond:4567/")
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
        return retrofit.create(ApiService::class.java)
    }

    /**
     * Tells the API to add a new treasure path to the database.
     */
    fun callNewTreasurePath(pathID: Long, userID: Long, message: String) {
        val call = apiService.newTreasurePath(pathID, userID, message)
        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Log.d("NEWTREASUREPATH", "Created new treasure path")
                } else {
                    Log.d("NEWTREASUREPATH", "Couldn't make a new treasure path: " + response.message())
                }
            }
            override fun onFailure(call: Call<Void>, t:Throwable) {
                Log.d("NEWTREASUREPATH", "Error: \n ${t.message}")
            }
        })
    }


    /**
     * Tells the API to add a new path point to the database.
     * A path point represents one coordinate in a treasure path.
     */
    fun callNewPathPoint(pathID: Long, pointNumber: Long, latitude: Double, longitude: Double) {
        Log.d("NEWPATHPOINT", "$pathID, $pointNumber, $latitude, $longitude")
        val call = apiService.newPathPoint(pathID, pointNumber, latitude, longitude)
        call.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Log.d("NEWPATHPOINT", "Created new path point")
                } else {
                    Log.d("NEWPATHPOINT", "Couldn't make a new path point: " + response.message())
                }
            }
            override fun onFailure(call: Call<Void>, t:Throwable) {
                Log.d("NEWPATHPOINT", "Error: \n ${t.message}")
            }
        })
    }

    /**
     * Sends a request to the API to get treasure paths from the database.
     * Paths are saved to the ArrayList "allPaths".
     */
    fun callGetPaths() {
        Log.d("GETPATHS", "Retrieving paths")
        val call = apiService.getPaths()
        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                Log.d("GETPATHS", "getPaths response successful: " + response.isSuccessful)
                if (response.isSuccessful) {
                    val responseString = response.body()?.string()
                    val objectMapper = jacksonObjectMapper()

                    if (responseString != null) {
                        Log.d("GETPATHS", "Got Paths")
                        allPaths = objectMapper.readValue(responseString)
                    }
                } else {
                    Log.d("GETPATHS", "Couldn't get paths")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.d("GETPATHS", "Error: \n ${t.message}")
            }
        })
    }

    /**
     * Shows a snackbar at the top of the screen instead of the default bottom position.
     * This stops it getting in the way of the buttons, which are at the bottom of the screen.
     */
    fun showTopSnackbar(view: View, displayText: String, snackbarDuration: Int) {
        val snackbar: Snackbar = Snackbar.make(view, displayText, snackbarDuration)
            .setAction("Action", null)
        val snackbarView = snackbar.view
        val layoutParams = snackbarView.layoutParams as FrameLayout.LayoutParams
        layoutParams.gravity = Gravity.TOP
        snackbarView.layoutParams = layoutParams
        val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.textSize = 18f
        textView.maxLines = 4
        textView.ellipsize = null
        snackbar.show()
    }

    /**
     * Shows a text box for the user to input a message to save in a treasure path.
     * Pressing the positive button will start pathing, and the negative one will cancel the prompt.
     */
    fun showMessageInput(context: Context, view: View) {
        val input = EditText(context)
        val container = LinearLayout(context)
        container.setPadding(16, 0, 16, 0)
        container.addView(input)

        val alertDialog = AlertDialog.Builder(context)
            .setTitle(R.string.hiding_message)
            .setView(container)
            .setPositiveButton(R.string.done) {_, _ ->
                startPathing(view, input.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
        alertDialog.show()
    }

}
