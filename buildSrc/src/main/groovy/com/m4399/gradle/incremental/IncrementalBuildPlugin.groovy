package com.m4399.gradle.incremental

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.m4399.gradle.incremental.tasks.IncrementalJavaCompilerTask
import com.m4399.gradle.incremental.utils.FileMonitor
import com.m4399.gradle.incremental.extention.IncrementalExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * By default, Gradle will disable incremental compile with javac when a modified java source contains constant field,
 * even though the constant value is the same as preview compile, which leads to spending more time in building process.
 *
 *
 */
class IncrementalBuildPlugin implements Plugin<Project> {

    static boolean loggable = true

    Project project

    int deviceSdkVersion = -1

    boolean isApplyBeforeAndroid = true

    @Override
    void apply(Project project) {

        this.project = project

        if (!canIncrementalBuild()) {
            return
        }

        if (project.hasProperty("android")) {
            isApplyBeforeAndroid = false
        }

        project.getExtensions().add(IncrementalExtension.NAME, IncrementalExtension)

        project.afterEvaluate {
            if (!project.hasProperty("android")) {
                project.println("${project.path}:incrementalBuildPlugin Plugin can only work with Android plugin")
                return
            }

            checkMinSdk()
            enablePreDexLibraries()

            project.android.applicationVariants.all { ApplicationVariantImpl variant ->
                createIncrementalBuildTask(variant)
            }
        }
    }

    void createIncrementalBuildTask(ApplicationVariantImpl variant) {

        if (!variant.buildType.name.equals("debug")) {
            return
        }

        if (loggable) {
            project.println("${project.path}:incrementalBuildPlugin Start creating incremental build task for ${variant.name}")
        }

        def taskContainer = project.tasks

        IncrementalJavaCompilerTask task = taskContainer.create("incremental${variant.name.capitalize()}JavaWithJavac", IncrementalJavaCompilerTask)
        task.variant = variant
        task.javaCompiler = variant.javaCompiler
        task.outputs.upToDateWhen { false }
        task.monitor = new FileMonitor(project, task.getIncrementalOutputs())
        task.dependsOn variant.mergeResources
        task.dependsOn variant.variantData.generateRClassTask

        variant.javaCompiler.dependsOn task

        def safeguardTask = taskContainer.getByName("incremental${variant.name.capitalize()}JavaCompilationSafeguard")
        if (safeguardTask != null) {
            safeguardTask.enabled = false
        }

        def taskGraph = project.gradle.taskGraph
        taskGraph.whenReady {
            if (taskGraph.getAllTasks().any { it.name.startsWith("assemble") }) {
            }
        }
    }

    boolean canIncrementalBuild() {
        if (!project.gradle.startParameter.taskNames.any { it.matches(":[^:]+:assemble(.*?)Debug") }) {
            return false
        }

        Properties prop = new Properties()
        File localProp = new File("${project.rootDir}/local.properties")
        if (!localProp.exists()) {
            return false
        }
        localProp.withInputStream { input ->
            prop.load(input)
        }
        return "true" == prop.getProperty("build.incremental", "false")
    }

    void enablePreDexLibraries() {
        IncrementalExtension extension = project.getExtensions().getByName(IncrementalExtension.NAME)
        if (extension != null && extension.disableAutoPreDex || deviceSdkVersion < 21) {
            return
        }
        if (!isApplyBeforeAndroid) {
            project.println("${project.path}:incrementalBuildPlugin You must apply this plugin before plugin: 'com.android.application' in build.gradle")
        }
        project.android.dexOptions.preDexLibraries = true
        if (loggable) {
            project.println("${project.path}:incrementalBuildPlugin enable android.dexOptions.preDexLibraries = true")
        }
    }

    void checkMinSdk() {

        if (deviceSdkVersion > 0) {
            return
        }

        for (String line : "adb devices".execute().getText().readLines()) {

            if (line.startsWith("List of devices attached")) {
                continue
            }

            int deviceIndex = line.indexOf("device")
            if (deviceIndex == -1) {
                continue
            }
            def deviceSerial = line.substring(0, deviceIndex).trim()
            String sdkInfo = "adb -s ${ deviceSerial} shell getprop ro.build.version.sdk".execute().getText().trim()
            if (sdkInfo.matches("\\d+")) {
                deviceSdkVersion = sdkInfo.toInteger()
                project.println("${project.path}:incrementalBuildPlugin device ${deviceSerial} sdk version ${sdkInfo}")
            }

            if (deviceSdkVersion < 21) {
                return
            }
        }

        project.android.defaultConfig.minSdkVersion = 21
        if (loggable) {
            project.println("${project.path}:incrementalBuildPlugin chage android.defaultConfig.minSdkVerion = 21")
        }
    }

}
