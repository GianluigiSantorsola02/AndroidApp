package com.example.toxicchat.androidapp.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ToxicityApi {
    @GET("health")
    suspend fun health(): HealthResponseDto

    @POST("toxicity/score")
    suspend fun score(@Body req: ScoreRequestDto): ScoreResponseDto
}