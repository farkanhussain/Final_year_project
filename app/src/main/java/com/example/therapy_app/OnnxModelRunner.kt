package com.example.therapy_app


import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.io.FileOutputStream


class OnnxModelRunner(private val context: Context) {

    private val env = OrtEnvironment.getEnvironment()
    private val sessionA: OrtSession
    private val sessionB: OrtSession

    init {
        sessionA = env.createSession(assetFilePath("modelA.onnx"))
        sessionB = env.createSession(assetFilePath("modelB.onnx"))
    }

    private fun assetFilePath(assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (!file.exists()) {
            context.assets.open(assetName).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file.absolutePath
    }

    fun runModelA(text: String): String {
        val input = arrayOf(arrayOf(text))
        val tensor = OnnxTensor.createTensor(env, input)
        val result = sessionA.run(mapOf("input" to tensor))

        val output = result[0].value

        // DEBUG
        return "DEBUG OUTPUT TYPE: ${output::class.java}, VALUE: $output"
    }



    fun runModelB(symptoms: FloatArray): String {
        // Model expects a 1D tensor: [24]
        val tensor = OnnxTensor.createTensor(env, symptoms)

        val result = sessionB.run(mapOf("input" to tensor))
        return result[0].value as String
    }




}
