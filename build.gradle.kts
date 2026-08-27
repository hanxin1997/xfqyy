plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

/**
 * 仓库不提交 gradle-wrapper.jar（二进制文件无法以文本方式维护）。
 * 需要 gradlew 时，用任意本地 gradle 执行一次 `gradle wrapper` 即可生成，
 * 版本在此固定，与 CI 使用的版本保持一致。
 */
tasks.wrapper {
    gradleVersion = "8.7"
    distributionType = Wrapper.DistributionType.BIN
}
