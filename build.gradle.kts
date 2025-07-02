@file:Suppress("PropertyName")

plugins {
  id("fabric-loom") version "1.11.1"
  id("maven-publish")
}

operator fun Project.get(name: String): String {
  return project.findProperty(name) as? String
    ?: throw IllegalArgumentException("Property '$name' is not set or not String.")
}

val Project.mod_version get() = this["mod_version"]
val Project.maven_group get() = this["maven_group"]
val Project.archives_base_name get() = this["archives_base_name"]
val Project.minecraft_version get() = this["minecraft_version"]
val Project.yarn_mappings get() = this["yarn_mappings"]
val Project.loader_version get() = this["loader_version"]
val Project.fabric_version get() = this["fabric_version"]

version = project.mod_version
group = project.maven_group

base {
  archivesName = project.archives_base_name
}

loom {
  splitEnvironmentSourceSets()

  mods {
    create("quicmc") {
      sourceSet(sourceSets.main.get())
      sourceSet(sourceSets.getByName("client"))
    }
  }
}

fabricApi {
  configureDataGeneration {
    client = true
  }
}

repositories {
  // Add repositories to retrieve artifacts from in here.
  // You should only use this when depending on other mods because
  // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
  // See https://docs.gradle.org/current/userguide/declaring_repositories.html
  // for more information about repositories.
}

dependencies {
  // To change the versions see the gradle.properties file
  minecraft("com.mojang:minecraft:${project.minecraft_version}")
  mappings("net.fabricmc:yarn:${project.yarn_mappings}:v2")
  modImplementation("net.fabricmc:fabric-loader:${project.loader_version}")

  modImplementation("net.fabricmc.fabric-api:fabric-api:${project.fabric_version}")

  compileOnly("com.google.code.findbugs:jsr305:3.0.2")

  // lombok
  val lombokVersion = "1.18.38"
  compileOnly("org.projectlombok", "lombok", lombokVersion)
  annotationProcessor("org.projectlombok", "lombok", lombokVersion)
  testCompileOnly("org.projectlombok", "lombok", lombokVersion)
  testAnnotationProcessor("org.projectlombok", "lombok", lombokVersion)
}

tasks.processResources.configure {
  inputs.property("version", project.version)
  inputs.property("minecraft_version", project.minecraft_version)
  inputs.property("loader_version", project.loader_version)
  setFilteringCharset("UTF-8")

  filesMatching("fabric.mod.json") {
    expand(
      "version" to project.version,
      "minecraft_version" to project.minecraft_version,
      "loader_version" to project.loader_version,
    )
  }
}

val targetJavaVersion = 17
tasks.withType<JavaCompile>().configureEach {
  // ensure that the encoding is set to UTF-8, no matter what the system default is
  // this fixes some edge cases with special characters not displaying correctly
  // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
  // If Javadoc is generated, this must be specified in that task too.
  options.encoding = "UTF-8"
  if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible()) {
    options.release.set(targetJavaVersion)
  }
}

java {
  val javaVersion = JavaVersion.toVersion(targetJavaVersion)
  if (JavaVersion.current() < javaVersion) {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
  }
  // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
  // if it is present.
  // If you remove this line, sources will not be generated.
  withSourcesJar()
}

tasks.named<Jar>("jar").configure {
  from("LICENSE") {
    rename { "${it}_${project.archives_base_name}" }
  }
}

// configure the maven publication
publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      artifactId = project.archives_base_name
      from(components["java"])
    }
  }

  // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
  repositories {
    // Add repositories to publish to here.
    // Notice: This block does NOT have the same function as the block in the top level.
    // The repositories here will be used for publishing your artifact, not for
    // retrieving dependencies.
  }
}
