import com.android.build.gradle.BaseExtension
import org.gradle.api.Project

val keystoreFile = file("kpfc-release.jks")
if (keystoreFile.exists()) {
    println("使用固定签名密钥: kpfc-release.jks")
    configure<BaseExtension> {
        signingConfigs {
            create("release") {
                storeFile = keystoreFile
                storePassword = "kpfc123"
                keyAlias = "kpfc"
                keyPassword = "kpfc123"
                enableV1Signing = true
                enableV2Signing = true
            }
        }
        buildTypes.findByName("release")?.signingConfig = signingConfigs.getByName("release")
    }
} else {
    println("警告: kpfc-release.jks 不存在，使用 debug 签名")
}