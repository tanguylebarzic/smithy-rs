/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.aws.traits.ServiceTrait
import kotlin.streams.toList

extra["displayName"] = "Smithy :: Rust :: AWS-SDK"
extra["moduleName"] = "software.amazon.smithy.rust.awssdk"

tasks["jar"].enabled = false

plugins {
    id("software.amazon.smithy").version("0.5.2")
}

val smithyVersion: String by project

val sdkOutputDir = buildDir.resolve("aws-sdk")
val awsServices = discoverServices()
// TODO: smithy-http should be removed
val runtimeModules = listOf("smithy-types", "smithy-http")

buildscript {
    val smithyVersion: String by project
    dependencies {
        classpath("software.amazon.smithy:smithy-aws-traits:$smithyVersion")
    }
}

dependencies {
    implementation(project(":codegen"))
    implementation("software.amazon.smithy:smithy-aws-protocol-tests:$smithyVersion")
    implementation("software.amazon.smithy:smithy-protocol-test-traits:$smithyVersion")
    implementation("software.amazon.smithy:smithy-aws-traits:$smithyVersion")
}

data class AwsService(val service: String, val module: String, val modelFile: File, val extraConfig: String? = null)

fun discoverServices(): List<AwsService>  {
    val models = project.file("models")
    return fileTree(models).map { file ->
        val model = Model.assembler().addImport(file.absolutePath).assemble().result.get()
        val services: List<ServiceShape> = model.shapes(ServiceShape::class.java).sorted().toList()
        if (services.size != 1) {
            throw Exception("There must be exactly one service in each aws model file")
        }
        val service = services[0]
        val sdkId = service.expectTrait(ServiceTrait::class.java).sdkId.toLowerCase()
        AwsService(service = service.id.toString(), module = sdkId, modelFile = file)
    }
}

fun generateSmithyBuild(tests: List<AwsService>): String {
    val projections = tests.joinToString(",\n") {
        """
            "${it.module}": {
                "imports": ["${it.modelFile.absolutePath}"],
                "plugins": {
                    "rust-codegen": {
                      "runtimeConfig": {
                        "relativePath": "../"
                      },
                      "service": "${it.service}",
                      "module": "${it.module}",
                      "moduleVersion": "0.0.1",
                      "build": {
                        "rootProject": true
                      }
                      ${it.extraConfig ?: ""}
                 }
               }
            }
        """.trimIndent()
    }
    return """
    {
        "version": "1.0",
        "projections": { $projections }
    }
    """
}


task("generateSmithyBuild") {
    description = "generate smithy-build.json"
    doFirst {
        projectDir.resolve("smithy-build.json").writeText(generateSmithyBuild(awsServices))
    }
    doLast {
        awsServices.forEach {
            copy {
                from("$buildDir/smithyprojections/aws-sdk/${it.module}/rust-codegen")
                into(sdkOutputDir.resolve(it.module))
            }
        }
    }
    finalizedBy("relocateRuntime")
}

tasks.register<Copy>("relocateRuntime") {
    from("$rootDir/rust-runtime") {
        runtimeModules.forEach {
            include("$it/**")
        }
        exclude("**/target")
    }
    into(sdkOutputDir)
}

fun generateCargoWorkspace(services: List<AwsService>): String {
    val modules = services.map(AwsService::module) + runtimeModules
    return """
    [workspace]
    members = [
        ${modules.joinToString(",") { "\"$it\"" }}
    ]
    """.trimIndent()
}
task("generateCargoWorkspace") {
    description = "generate Cargo.toml workspace file"
    doFirst {
        sdkOutputDir.resolve("Cargo.toml").writeText(generateCargoWorkspace(awsServices))
    }
}

tasks["smithyBuildJar"].dependsOn("generateSmithyBuild")
tasks["assemble"].finalizedBy("generateCargoWorkspace")


tasks.register<Exec>("cargoCheck") {
    workingDir(buildDir.resolve("aws-sdk"))
    // disallow warnings
    environment("RUSTFLAGS", "-D warnings")
    commandLine("cargo", "check")
    dependsOn("assemble")
}

tasks.register<Exec>("cargoTest") {
    workingDir(buildDir.resolve("aws-sdk"))
    // disallow warnings
    environment("RUSTFLAGS", "-D warnings")
    commandLine("cargo", "test")
    dependsOn("assemble")
}

tasks.register<Exec>("cargoDocs") {
    workingDir(buildDir.resolve("aws-sdk"))
    // disallow warnings
    environment("RUSTFLAGS", "-D warnings")
    commandLine("cargo", "doc", "--no-deps")
    dependsOn("assemble")
}

tasks.register<Exec>("cargoClippy") {
    workingDir(buildDir.resolve("aws-sdk"))
    // disallow warnings
    environment("RUSTFLAGS", "-D warnings")
    commandLine("cargo", "clippy")
    dependsOn("assemble")
}

tasks["test"].dependsOn("cargoCheck", "cargoClippy", "cargoTest", "cargoDocs")

tasks["clean"].doFirst {
    delete("smithy-build.json")
}