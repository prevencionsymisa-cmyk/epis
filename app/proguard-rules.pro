# Modelos que Gson (de)serializa por reflexión
-keep class com.episcan.app.ota.UpdateInfo { *; }
-keepattributes Signature, *Annotation*

# Retrofit
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation interface com.episcan.app.data.remote.GeminiApi
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

-keep class com.episcan.app.sync.EpiDto { *; }
-keep class com.episcan.app.sync.PeticionPush { *; }
-keep class com.episcan.app.sync.ResultadoItemPush { *; }
-keep class com.episcan.app.sync.RespuestaPush { *; }
-keep class com.episcan.app.sync.RespuestaPull { *; }
