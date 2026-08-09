# Minification désactivée par défaut dans app/build.gradle.kts (isMinifyEnabled = false).
# Si tu l'actives un jour, garde ces règles de base pour la WebView :
-keepclassmembers class * extends android.webkit.WebChromeClient
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

