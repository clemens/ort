/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.clients.clearlydefined

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody

import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Interface for the ClearlyDefined REST API, based on code generated by https://app.quicktype.io/ from
 * https://github.com/clearlydefined/service/tree/master/schemas.
 */
interface ClearlyDefinedService {
    companion object {
        /**
         * The JSON (de-)serialization object used by this service.
         */
        val JSON = Json { encodeDefaults = false }

        /**
         * Create a ClearlyDefined service instance for communicating with the given [server], optionally using a
         * pre-built OkHttp [client].
         */
        fun create(server: Server, client: OkHttpClient? = null): ClearlyDefinedService =
            create(server.url, client)

        /**
         * Create a ClearlyDefined service instance for communicating with a server running at the given [url],
         * optionally using a pre-built OkHttp [client].
         */
        fun create(url: String? = null, client: OkHttpClient? = null): ClearlyDefinedService {
            val contentType = "application/json".toMediaType()
            val retrofit = Retrofit.Builder()
                .apply { if (client != null) client(client) }
                .baseUrl(url ?: Server.PRODUCTION.url)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(JSON.asConverterFactory(contentType))
                .build()

            return retrofit.create(ClearlyDefinedService::class.java)
        }
    }

    /**
     * See https://github.com/clearlydefined/service/blob/661934a/schemas/swagger.yaml#L8-L14.
     */
    enum class Server(val url: String) {
        /**
         * The ClearlyDefined production server. When submitting curations, this will create PRs against the repository
         * at https://github.com/clearlydefined/curated-data.
         */
        PRODUCTION("https://api.clearlydefined.io"),

        /**
         * The ClearlyDefined development server. When submitting curations, this will create PRs against the repository
         * at https://github.com/clearlydefined/curated-data-dev.
         */
        DEVELOPMENT("https://dev-api.clearlydefined.io"),

        /**
         * The ClearlyDefined server when running locally.
         */
        LOCAL("http://localhost:4000")
    }

    /**
     * The return type for https://api.clearlydefined.io/api-docs/#/definitions/post_definitions.
     */
    @Serializable
    data class Defined(
        val coordinates: Coordinates,
        val described: Described,
        val licensed: Licensed,
        val files: List<FileEntry>? = null,
        val scores: FinalScore,

        @SerialName("_id")
        val id: String? = null,

        @SerialName("_meta")
        val meta: Meta
    ) {
        /**
         * Return the harvest status of a described component, also see
         * https://github.com/clearlydefined/website/blob/de42d2c/src/components/Navigation/Ui/HarvestIndicator.js#L8.
         */
        fun getHarvestStatus() =
            when {
                described.tools == null -> HarvestStatus.NOT_HARVESTED
                described.tools.size > 2 -> HarvestStatus.HARVESTED
                else -> HarvestStatus.PARTIALLY_HARVESTED
            }
    }

    /**
     * See https://github.com/clearlydefined/service/blob/4e210d7/schemas/swagger.yaml#L84-L101.
     */
    @Serializable
    data class ContributionPatch(
        val contributionInfo: ContributionInfo,
        val patches: List<Patch>
    )

    /**
     * See https://github.com/clearlydefined/service/blob/4e210d7/schemas/swagger.yaml#L87-L97.
     */
    @Serializable
    data class ContributionInfo(
        val type: ContributionType,

        /**
         * Short (100 char) description. This will also be used as the PR title.
         */
        val summary: String,

        /**
         * Describe here the problem(s) being addressed.
         */
        val details: String,

        /**
         * What does this PR do to address the issue? Include references to docs where the new data was found and, for
         * example, links to public conversations with the affected project team.
         */
        val resolution: String,

        /**
         * Remove contributed definitions from the list.
         */
        val removedDefinitions: Boolean
    )

    /**
     * See https://github.com/clearlydefined/service/blob/53acc01/routes/curations.js#L86-L89.
     */
    @Serializable
    data class ContributionSummary(
        val prNumber: Int,
        val url: String
    )

    /**
     * See https://github.com/clearlydefined/service/blob/4917725/schemas/harvest-1.0.json#L12-L22.
     */
    @Serializable
    data class HarvestRequest(
        val tool: String? = null,
        val coordinates: String,
        val policy: String? = null
    )

    /**
     * Return a batch of definitions for the components given as [coordinates], see
     * https://api.clearlydefined.io/api-docs/#/definitions/post_definitions.
     */
    @POST("definitions")
    suspend fun getDefinitions(@Body coordinates: Collection<Coordinates>): Map<Coordinates, Defined>

    /**
     * Search for existing definitions based on the [pattern] string provided, see
     * https://api.clearlydefined.io/api-docs/#/definitions/get_definitions. This function represents the part of
     * the definition's endpoint that allows searching for package coordinates based on a pattern. The pattern string
     * should contain the parts of the coordinates (typically namespace, name, and version) relevant for the search.
     * Result is a list with the ClearlyDefined URIs to all the definitions that are matched by the pattern.
     */
    @GET("definitions")
    suspend fun searchDefinitions(@Query("pattern") pattern: String): List<String>

    /**
     * Get the curation for the component described by [type], [provider], [namespace], [name] and [revision], see
     * https://api.clearlydefined.io/api-docs/#/curations/get_curations__type___provider___namespace___name___revision_.
     */
    @GET("curations/{type}/{provider}/{namespace}/{name}/{revision}")
    suspend fun getCuration(
        @Path("type") type: ComponentType,
        @Path("provider") provider: Provider,
        @Path("namespace") namespace: String,
        @Path("name") name: String,
        @Path("revision") revision: String
    ): Curation

    /**
     * Return a batch of curations for the components given as [coordinates], see
     * https://api.clearlydefined.io/api-docs/#/curations/post_curations_.
     */
    @POST("curations")
    suspend fun getCurations(@Body coordinates: Collection<Coordinates>): Map<Coordinates, ContributedCurations>

    /**
     * Upload curation [patch] data, see https://api.clearlydefined.io/api-docs/#/curations/patch_curations.
     */
    @PATCH("curations")
    suspend fun putCuration(@Body patch: ContributionPatch): ContributionSummary

    /**
     * [Request][request] the given components to be harvested, see
     * https://api.clearlydefined.io/api-docs/#/harvest/post_harvest.
     */
    @POST("harvest")
    suspend fun harvest(@Body request: Collection<HarvestRequest>): String

    /**
     * Get information about the harvest tools that have produced data for the component described by [type],
     * [provider], [namespace], [name], and [revision], see
     * https://api.clearlydefined.io/api-docs/#/harvest/get_harvest__type___provider___namespace___name___revision_.
     * This can be used to quickly find out whether results of a specific tool are already available.
     */
    @GET("harvest/{type}/{provider}/{namespace}/{name}/{revision}?form=list")
    suspend fun harvestTools(
        @Path("type") type: ComponentType,
        @Path("provider") provider: Provider,
        @Path("namespace") namespace: String,
        @Path("name") name: String,
        @Path("revision") revision: String
    ): List<String>

    /**
     * Get the harvested data for the component described by [type], [provider], [namespace], [name], and [revision]
     * that was produced by [tool] with version [toolVersion], see
     * https://api.clearlydefined.io/api-docs/#/harvest/get_harvest__type___provider___namespace___name___revision___tool___toolVersion_
     */
    @GET("harvest/{type}/{provider}/{namespace}/{name}/{revision}/{tool}/{toolVersion}?form=streamed")
    suspend fun harvestToolData(
        @Path("type") type: ComponentType,
        @Path("provider") provider: Provider,
        @Path("namespace") namespace: String,
        @Path("name") name: String,
        @Path("revision") revision: String,
        @Path("tool") tool: String,
        @Path("toolVersion") toolVersion: String
    ): ResponseBody
}
