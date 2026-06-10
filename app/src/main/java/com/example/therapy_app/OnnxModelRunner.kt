package com.example.therapy_app


import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import android.util.Log
import ai.onnxruntime.TensorInfo


class OnnxModelRunner(private val context: Context) {

    private val env = OrtEnvironment.getEnvironment()
    private val sessionA: OrtSession

    private val sessionB: OrtSession





    private val featureCols = listOf(
        "feeling.nervous",
        "panic",
        "breathing.rapidly",
        "sweating",
        "trouble.in.concentration",
        "having.trouble.in.sleeping",
        "having.trouble.with.work",
        "hopelessness",
        "anger",
        "over.react",
        "change.in.eating",
        "suicidal.thought",
        "feeling.tired",
        "close.friend",
        "social.media.addiction",
        "weight.gain",
        "material.possessions",
        "introvert",
        "popping.up.stressful.memory",
        "having.nightmares",
        "avoids.people.or.activities",
        "feeling.negative",
        "trouble.concentrating",
        "blamming.yourself"
    )

    private val featureIndexMap: Map<String, Int> = featureCols.mapIndexed { i, name -> name to i }.toMap()

    // Keyword map (reuse or extend inside ONNXModelRunner)
    private val KEYWORDS: Map<String, List<String>> = mapOf(
        "feeling.nervous" to listOf("nervous", "on edge", "shaky"),
        "panic" to listOf("panic attack", "panic"),
        "breathing.rapidly" to listOf("can't breathe", "breathing rapidly", "short of breath"),
        "sweating" to listOf("sweat", "sweating", "clammy"),
        "trouble.in.concentration" to listOf("can't concentrate", "trouble concentrating", "hard to focus"),
        "having.trouble.in.sleeping" to listOf("can't sleep", "insomnia", "awake all night"),
        "having.trouble.with.work" to listOf("can't work", "trouble at work", "can't focus at work"),
        "hopelessness" to listOf("hopeless", "no hope", "give up"),
        "anger" to listOf("angry", "furious", "irritated", "mad"),
        "over.react" to listOf("overreact", "snapped", "too sensitive", "melt down"),
        "change.in.eating" to listOf("eating more", "eating less", "lost appetite", "overeating"),
        "suicidal.thought" to listOf("suicidal", "want to die", "kill myself", "end my life"),
        "feeling.tired" to listOf("tired", "exhausted", "no energy"),
        "close.friend" to listOf("no close friend", "no friends", "no one to talk to"),
        "social.media.addiction" to listOf("doomscroll", "social media", "instagram", "tiktok", "addicted"),
        "weight.gain" to listOf("weight gain", "gained weight"),
        "material.possessions" to listOf("material possessions", "things matter", "buying things"),
        "introvert" to listOf("introvert", "prefer to be alone", "shy"),
        "popping.up.stressful.memory" to listOf("flashback", "popping up memory", "reliving"),
        "having.nightmares" to listOf("nightmare", "bad dream", "nightmares"),
        "avoids.people.or.activities" to listOf("avoid people", "avoid activities", "isolating"),
        "feeling.negative" to listOf("feeling negative", "negative thoughts", "down on myself"),
        "trouble.concentrating" to listOf("trouble concentrating", "can't focus", "mind wanders"),
        "blamming.yourself" to listOf("blame myself", "my fault", "guilty", "ashamed")
    )


    init {
        sessionA = env.createSession(assetFilePath("modelA.onnx"))
        sessionB = env.createSession(assetFilePath("modelB.onnx"))

        logAllModelInputs()
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
        Log.e("ModelB", ">>> CALL STACK <<<", Exception("STACK"))
        Log.e("ModelB", ">>> runModelB CALLED FROM SOMEWHERE <<<")

        Log.e("ModelB", "Incoming symptoms length = ${symptoms.size}")
        Log.e("ModelB", "Symptoms = ${symptoms.joinToString()}")

        if (symptoms.size != 24) {
            throw IllegalArgumentException("Model B requires exactly 24 features, got ${symptoms.size}")
        }

        val inputInfoEntry = sessionB.inputInfo.entries.first()
        val inputName = inputInfoEntry.key
        val inputInfo = inputInfoEntry.value.info as TensorInfo

        Log.e("ModelB", "Model input name = $inputName")
        Log.e("ModelB", "Model input type = ${inputInfo.type}")
        Log.e("ModelB", "Model input shape = ${inputInfo.shape.contentToString()}")
        Log.e("ModelB", "Model input rank = ${inputInfo.shape.size}")

        val shape = longArrayOf(1, symptoms.size.toLong())
        val buffer = FloatBuffer.wrap(symptoms)
        val tensor = OnnxTensor.createTensor(env, buffer, shape)

        var result: OrtSession.Result? = null
        try {
            result = sessionB.run(mapOf(inputName to tensor))

            // Output 0 = predicted label index
            val labelRaw = result[0].value
            val predictedLabelIndex = when (labelRaw) {
                is LongArray -> labelRaw[0].toInt()
                is IntArray -> labelRaw[0]
                is Array<*> -> labelRaw[0].toString().toInt()
                else -> labelRaw.toString().toInt()
            }

            // ⭐ EXACT LOCATION: define your labels here
            val disorderLabels = listOf("Anxiety", "Depression", "Loneliness", "Stress", "Normal")
            val disorder = disorderLabels[predictedLabelIndex]

            // (Optional) log probabilities for debugging
            val probRaw = result[1].value
            val probs: Map<Long, Float> = when (probRaw) {
                is ai.onnxruntime.OnnxMap -> probRaw.value as Map<Long, Float>
                is Map<*, *> -> probRaw as Map<Long, Float>
                else -> emptyMap()
            }
            Log.e("ModelB", "Probabilities = $probs")

            // ⭐ Return ONLY the disorder label
            return disorder

        } finally {
            try { tensor.close() } catch (_: Exception) {}
            try { result?.close() } catch (_: Exception) {}
        }
    }






    fun logAllModelInputs() {
        Log.e("ModelB", "===== MODEL B INPUTS =====")
        for ((name, info) in sessionB.inputInfo) {
            val t = info.info as TensorInfo
            Log.e("ModelB", "Input name: $name")
            Log.e("ModelB", "  Type: ${t.type}")
            Log.e("ModelB", "  Shape: ${t.shape.contentToString()}")
        }
    }















}
