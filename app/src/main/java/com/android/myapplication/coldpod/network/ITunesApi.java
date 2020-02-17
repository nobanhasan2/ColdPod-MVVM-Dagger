package com.android.myapplication.coldpod.network;

import androidx.lifecycle.LiveData;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Url;

public interface ITunesApi {
    @GET("{country}/podcasts/top-podcasts/all/50/explicit.json")
    LiveData<ApiResponse<ITunesResponse>> getTopPodcasts(
            @Path("country") String country
    );

    /**
     * Reference: @see "https://stackoverflow.com/questions/32559333/retrofit-2-dynamic-url"
     * @param url @Url parameter annotation allows passing a complete URL for an endpoint.
     *            will overrid the baseUrl anyway
     * @param id The id is used to create a lookup request to search for a specific podcast
     */
    @GET
    LiveData<ApiResponse<LookupResponse>> getLookupResponse(
            @Url String url,
            @Query("id") String id
    );
}
