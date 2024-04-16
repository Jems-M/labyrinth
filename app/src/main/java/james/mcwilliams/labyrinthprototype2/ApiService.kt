package james.mcwilliams.labyrinthprototype2

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    @POST("newTreasurePath")
    fun newTreasurePath(
        @Query("pathID") pathID: Long,
        @Query("userID") userID: Long,
        @Body message: String
    ): Call<Void>

    @POST("newPathPoint")
    fun newPathPoint(
        @Query("pathID") pathID: Long,
        @Query("pointNumber") userID: Long,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): Call<Void>

    @GET("getPaths")
    fun getPaths(): Call<ResponseBody>
}