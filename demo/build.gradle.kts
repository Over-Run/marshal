dependencies {
    implementation(rootProject)
    annotationProcessor(rootProject)
}

tasks.withType<Jar> {
    archiveBaseName = "marshal-demo"
}
