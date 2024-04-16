package james.mcwilliams.labyrinthprototype2

import android.location.Location
import com.google.android.gms.maps.model.LatLng
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

class TreasurePath() {
    var pathID = 0L
    var userID = 0L
    var message = "Hello Labyrinth!"
    var timeCreated: Long = 0

    val coordinates: MutableList<LatLng> = mutableListOf()

    val pathPoints = arrayOf<Double>()

    fun addCoordinate(locations: MutableList<Location>) {
        for (location in locations) {
            coordinates.add(locationToLatLng(location))
        }
    }


    /**
     * Returns the distance between two LatLngs.
     * Less accurate than Android's built in function, but makes that sacrifice in exchange
     * for being potentially much faster.
     */
    fun getCoordinateDistance(pos1: LatLng, pos2: LatLng): Double {
        val lat1Radians = Math.toRadians(pos1.latitude)
        val lon1Radians = Math.toRadians(pos1.longitude)
        val lat2Radians = Math.toRadians(pos2.latitude)
        val lon2Radians = Math.toRadians(pos2.longitude)

        val distance = acos(sin(lat1Radians) * sin(lat2Radians)
                + cos(lat1Radians) * cos(lat2Radians)
                * cos(lon2Radians - lon1Radians)) * 6378.1370 // approx. radius of earth in km

        return distance
    }

    fun clear() {
        coordinates.clear()
        pathID = (0..Long.MAX_VALUE).random()
    }

    /**
     * Checks if this path fits within another path. To be specific, if every node on this path
     * is close enough to a node on the given path, this function returns true.
     */
    fun compareToPath(otherPath: TreasurePath): Double {
        val allMatches: MutableList<Int> = ArrayList()
        for (i in coordinates) {
            var matched = false
            for (j in otherPath.coordinates) {
                if (getCoordinateDistance(i, j) < 0.01) {
                    matched = true
                    break //found a match, don't check the rest
                }
            }
            // false = 0 and true = 1
            // so if matched is false, compareTo will return 0, and otherwise will return 1
            allMatches.add(matched.compareTo(false))
        }
        val matchRate: Double = (allMatches.sum().toDouble() / allMatches.size.toDouble())
        return matchRate
    }

    fun locationToLatLng(location: Location): LatLng {
        return LatLng(location.latitude, location.longitude)
    }

    /**
     * Coordinates are saved as "pathPoints" API-side. They're saved in a single string,
     * formatted [lat,lng,lat,lng,lat,lng] and so on. I would spend time fixing this, but
     * I'm already quite behind on the project, so this function will do as a patch job.
     */
    fun pathPointsToCoordinates() {
        for (i in pathPoints.indices step 2) {
            val lat = pathPoints[i]
            val lng = pathPoints[i+1]
            coordinates.add(LatLng(lat,lng))
        }
    }

}