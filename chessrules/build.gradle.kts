plugins { `java-library` }
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
sourceSets.main { java.srcDir("../vendor/chesslib/src/main/java") }
dependencies { api("org.apache.commons:commons-lang3:3.17.0") }
