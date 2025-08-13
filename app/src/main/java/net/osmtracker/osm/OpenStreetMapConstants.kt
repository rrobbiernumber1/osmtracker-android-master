package net.osmtracker.osm

object OpenStreetMapConstants {

	private const val DEV_MODE: Boolean = false
	private const val OSM_API_URL_DEV: String = "https://master.apis.dev.openstreetmap.org"
	private const val OSM_API_URL_PROD: String = "https://www.openstreetmap.org"
	private val OSM_API_URL: String = if (DEV_MODE) OSM_API_URL_DEV else OSM_API_URL_PROD

	object Api {
		@JvmField
		val OSM_API_URL_PATH: String = "$OSM_API_URL/api/0.6/"
	}

	object OAuth2 {
		@JvmField
		val CLIENT_ID_PROD: String = "6s8TuIQoPeq89ZWUFOXU7EZ-ZaCUVtUoNZFIKCMdU-E"
		@JvmField
		val CLIENT_ID_DEV: String = "94Ht-oVBJ2spydzfk18s1RV2z7NS98SBwMfzSCqLQLE"

		@JvmField
		val CLIENT_ID: String = if (DEV_MODE) CLIENT_ID_DEV else CLIENT_ID_PROD

		@JvmField
		val SCOPE: String = "write_gpx"
		@JvmField
		val USER_AGENT: String = "OSMTracker for Android™"

		object Urls {
			@JvmField
			val AUTHORIZATION_ENDPOINT: String = "$OSM_API_URL/oauth2/authorize"
			@JvmField
			val TOKEN_ENDPOINT: String = "$OSM_API_URL/oauth2/token"
		}
	}
}


