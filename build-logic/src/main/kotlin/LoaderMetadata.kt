import kotlinx.serialization.Serializable

@Serializable
data class FabricManifest(
	val schemaVersion: Int = 1,
	val id: String,
	val name: String,
	val version: String,
	val authors: List<String>,
	val contributors: List<String>,
	val contact: Map<String, String>,
	val description: String,
	val icon: String,
	val license: String,
	val environment: String = "*",
	val entrypoints: Map<String, List<String>>,
	val depends: Map<String, String> = emptyMap(),
	val recommends: Map<String, String> = emptyMap(),
	val breaks: Map<String, String> = emptyMap(),
	val provides: List<String> = emptyList()
)

@Serializable
data class ForgeManifest(
	val modLoader: String = "javafml",
	val loaderVersion: String = "[2,)",
	val license: String,
	val issueTrackerURL: String,
	val mods: List<ForgeMod>,
	val dependencies: Map<String, List<ForgeDependency>> = emptyMap(),
)

@Serializable
data class ForgeMod(
	val modId: String,
	val displayName: String,
	val version: String,
	val displayURL: String,
	val modUrl: String,
	val logoFile: String,
	val authors: String,
	val logoBlur: Boolean = false,
	val credits: String,
	val description: String
)

@Serializable
data class ForgeDependency(
	val modId: String,
	val side: String,
	val versionRange: String,
	val mandatory: Boolean,
	val type: String
)


