package dev.pocketdev.runtime

data class RuntimeLaunchPlan(
    val executable: String,
    val argv: List<String>,
    val environment: Map<String, String>,
    val workingDirectory: String,
) {
    init {
        require(executable.isNotBlank()) { "executable must not be blank" }
        require(argv.isNotEmpty()) { "argv must not be empty" }
        require(argv.first() == executable) { "argv[0] must be executable" }
        require(workingDirectory.isNotBlank()) { "workingDirectory must not be blank" }
    }

    val envArray: Array<String>
        get() = environment.map { (key, value) -> "$key=$value" }.toTypedArray()
}
