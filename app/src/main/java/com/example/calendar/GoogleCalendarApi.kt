package com.example.calendar

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class CalendarEventDateTime(
  val dateTime: String? = null,
  val date: String? = null,
  val timeZone: String? = null,
)

@JsonClass(generateAdapter = true)
data class CalendarReminderOverride(
  val method: String = "popup",
  val minutes: Int = 60,
)

@JsonClass(generateAdapter = true)
data class CalendarReminders(
  val useDefault: Boolean = false,
  val overrides: List<CalendarReminderOverride>? = null,
)

@JsonClass(generateAdapter = true)
data class CalendarExtendedProperties(
  @param:Json(name = "private") val privateProps: Map<String, String>? = null,
)

@JsonClass(generateAdapter = true)
data class GoogleCalendarEventDto(
  val id: String? = null,
  val summary: String? = null,
  val description: String? = null,
  val location: String? = null,
  val colorId: String? = null,
  val start: CalendarEventDateTime? = null,
  val end: CalendarEventDateTime? = null,
  val reminders: CalendarReminders? = null,
  val extendedProperties: CalendarExtendedProperties? = null,
  val htmlLink: String? = null,
)

@JsonClass(generateAdapter = true)
data class GoogleCalendarEventsResponse(
  val items: List<GoogleCalendarEventDto> = emptyList(),
)

interface GoogleCalendarApi {
  @GET("calendars/primary/events")
  suspend fun listUpcomingEvents(
    @Header("Authorization") bearerToken: String,
    @Query("timeMin") timeMinIso: String,
    @Query("singleEvents") singleEvents: Boolean = true,
    @Query("orderBy") orderBy: String = "startTime",
    @Query("maxResults") maxResults: Int = 25,
  ): Response<GoogleCalendarEventsResponse>

  @POST("calendars/primary/events")
  suspend fun createEvent(
    @Header("Authorization") bearerToken: String,
    @Body event: GoogleCalendarEventDto,
  ): Response<GoogleCalendarEventDto>

  @PATCH("calendars/primary/events/{eventId}")
  suspend fun patchEvent(
    @Header("Authorization") bearerToken: String,
    @Path("eventId") eventId: String,
    @Body event: GoogleCalendarEventDto,
  ): Response<GoogleCalendarEventDto>

  @DELETE("calendars/primary/events/{eventId}")
  suspend fun deleteEvent(
    @Header("Authorization") bearerToken: String,
    @Path("eventId") eventId: String,
  ): Response<Unit>

  companion object {
    private const val BASE_URL = "https://www.googleapis.com/calendar/v3/"

    fun create(): GoogleCalendarApi {
      val logger =
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
      val client = OkHttpClient.Builder().addInterceptor(logger).build()
      return Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(GoogleCalendarApi::class.java)
    }
  }
}
