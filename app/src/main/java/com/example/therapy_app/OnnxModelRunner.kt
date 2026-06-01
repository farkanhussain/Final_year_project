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

    private fun normalizeTextForMatching(text: String): String {
        return text.lowercase()
            .replace("’", "'")
            .replace(Regex("[^\\w\\s']"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    private fun containsAnyWord(normalized: String, patterns: List<String>): Boolean {
        return patterns.any { pat ->
            val escaped = Regex.escape(pat)
            Regex("\\b$escaped\\b").containsMatchIn(normalized)
        }
    }

    private fun logExtractedState(state: FloatArray) {
        Log.d("ModelB", "Extracted features: ${state.joinToString()}")
    }

    // Assumes these helpers/constants exist in the same file/class:
// - featureIndexMap: Map<String, Int>
// - KEYWORDS: Map<String, List<String>>
// - normalizeTextForMatching(text: String): String
// - containsAnyWord(normalized: String, patterns: List<String>): Boolean
// - logExtractedState(state: FloatArray)

    private fun extractFeaturesForModelB(
        text: String,
        explicitAnswers: Map<String, Boolean>? = null, // optional: {"panic": true, "suicidal.thought": false}
        lastAskedFeature: String? = null // optional: used when user replies "yes"/"no"
    ): FloatArray {
        // Normalize once for reliable matching
        val normalized = normalizeTextForMatching(text)

        // 1) Start fresh
        val state = FloatArray(featureCols.size) { 0f }

        // 2) Apply explicit yes/no answers first (if provided) - explicit overrides free-text
        explicitAnswers?.forEach { (feature, value) ->
            featureIndexMap[feature]?.let { idx ->
                state[idx] = if (value) 1f else 0f
            }
        }

        // 3) Apply keyword detection only for features not already set by explicitAnswers
        for ((feature, keys) in KEYWORDS) {
            val idx = featureIndexMap[feature] ?: continue
            if (state[idx] == 1f) continue // explicit answer already set it
            if (containsAnyWord(normalized, keys)) state[idx] = 1f
        }

        // 4) Handle single-word yes/no replies mapped to lastAskedFeature
        val trimmed = normalized.trim()
        if ((trimmed == "yes" || trimmed == "no") && lastAskedFeature != null) {
            featureIndexMap[lastAskedFeature]?.let { idx ->
                state[idx] = if (trimmed == "yes") 1f else 0f
            }
        }

        // 5) Ensure strict binary values (0f or 1f)
        for (i in state.indices) state[i] = if (state[i] >= 1f) 1f else 0f

        // 6) Optional debug log
        logExtractedState(state)

        return state
    }

    fun runModelB(symptoms: FloatArray): String {
        // Inspect model input metadata (assumes `sessionB` and `env` are available)
        val inputInfoEntry = sessionB.inputInfo.entries.firstOrNull()
            ?: throw IllegalStateException("Model has no inputs")
        val inputName = inputInfoEntry.key

        // Cast NodeInfo.Info to TensorInfo to access shape safely
        val tensorInfo = inputInfoEntry.value.info as? TensorInfo
        val expectedShape: LongArray = tensorInfo?.shape ?: longArrayOf()
        val expectedRank = expectedShape.size

        Log.d("ONNX", "Model input name=$inputName expectedShape=${expectedShape.joinToString()} expectedRank=$expectedRank")
        Log.d("ONNX", "Prepared symptom vector length=${symptoms.size}")

        // Build tensor according to expected rank
        val tensor: OnnxTensor = try {
            when (expectedRank) {
                1 -> {
                    // Model expects [N]
                    val fb = FloatBuffer.wrap(symptoms)
                    OnnxTensor.createTensor(env, fb, longArrayOf(symptoms.size.toLong()))
                }
                2 -> {
                    // Model expects [batch, N] (e.g., [1, N])
                    val twoD: Array<FloatArray> = arrayOf(symptoms)
                    OnnxTensor.createTensor(env, twoD)
                }
                else -> throw IllegalArgumentException("Unsupported model input rank: $expectedRank. Expected 1 or 2.")
            }
        } catch (e: Exception) {
            Log.e("ONNX", "Failed to create input tensor: ${e.message}")
            throw e
        }

        // Validate the tensor we actually created against model expectation
        try {
            val sentInfo = tensor.info as? TensorInfo
            val sentShape = sentInfo?.shape ?: longArrayOf()
            Log.d("ONNX", "Created tensor shape=${sentShape.joinToString()} type=${sentInfo?.type}")

            // Determine feature dims for comparison (treat dynamic dims <= 0 as unknown)
            val expectedFeatureDim: Long = when (expectedRank) {
                1 -> if (expectedShape.isNotEmpty()) expectedShape[0] else -1L
                2 -> if (expectedShape.size >= 2) expectedShape[1] else -1L
                else -> -1L
            }
            val sentFeatureDim: Long = when (sentShape.size) {
                1 -> sentShape[0]
                2 -> sentShape[1]
                else -> -1L
            }

            if (expectedFeatureDim > 0 && sentFeatureDim > 0 && expectedFeatureDim != sentFeatureDim) {
                tensor.close()
                throw IllegalArgumentException("Feature length mismatch: model expects $expectedFeatureDim but tensor has $sentFeatureDim")
            }
        } catch (e: Exception) {
            // If validation fails, ensure tensor is closed and rethrow
            try { tensor.close() } catch (_: Exception) {}
            throw e
        }

        // Run inference and extract output safely
        var result: OrtSession.Result? = null
        try {
            result = sessionB.run(mapOf(inputName to tensor))

            // Inspect result value
            val raw = result[0].value

            // Handle common output types
            return when (raw) {
                is FloatArray -> raw.joinToString(prefix = "[", postfix = "]") { it.toString() }
                is DoubleArray -> raw.joinToString(prefix = "[", postfix = "]") { it.toString() }
                is LongArray -> raw.joinToString(prefix = "[", postfix = "]") { it.toString() }
                is IntArray -> raw.joinToString(prefix = "[", postfix = "]") { it.toString() }
                is Array<*> -> {
                    // If nested arrays, try to stringify first element or flatten common numeric arrays
                    val first = raw.getOrNull(0)
                    when (first) {
                        is FloatArray -> first.joinToString(prefix = "[", postfix = "]") { it.toString() }
                        is DoubleArray -> first.joinToString(prefix = "[", postfix = "]") { it.toString() }
                        is LongArray -> first.joinToString(prefix = "[", postfix = "]") { it.toString() }
                        is IntArray -> first.joinToString(prefix = "[", postfix = "]") { it.toString() }
                        else -> first?.toString() ?: raw.toString()
                    }
                }
                else -> raw?.toString() ?: "no_output"
            }
        } catch (e: Exception) {
            Log.e("ONNX", "Inference failed: ${e.message}")
            throw e
        } finally {
            // Always release resources
            try { tensor.close() } catch (_: Exception) {}
            try { result?.close() } catch (_: Exception) {}
        }
    }

    fun getSymptomVectorFromState(state: FloatArray): FloatArray {
        val expectedLen = featureCols.size
        return FloatArray(expectedLen) { idx ->
            val v = if (idx < state.size) state[idx] else 0f
            when {
                v.isNaN() -> 0f
                v < 0f -> 0f
                else -> if (v >= 1f) 1f else 0f
            }
        }
    }

    // 3) Tensor creation helper that respects model rank expectations
//    Returns the created OnnxTensor (caller must close it)
    fun createTensorFromVector(vector: FloatArray): OnnxTensor {
        // Create a 1-D tensor by default. If your model expects [batch, N], runModelB will wrap it.
        val fb = FloatBuffer.wrap(vector)
        return OnnxTensor.createTensor(env, fb, longArrayOf(vector.size.toLong()))
    }

    /**
     * Convenience wrapper: build features from free text (and optional explicit answers)
     * then run the model. Returns the same String result as runModelB.
     */
    fun runModelBFromText(
        text: String,
        explicitAnswers: Map<String, Boolean>? = null,
        lastAskedFeature: String? = null
    ): String {
        // 1) Extract features from text (fresh state)
        val state = extractFeaturesForModelB(text, explicitAnswers, lastAskedFeature)

        // 2) Sanitize / ensure correct length and binary values
        val vector = getSymptomVectorFromState(state)

        // 3) Optional debug log
        Log.d("ONNX", "Vector to send: ${vector.joinToString()}")

        // 4) Call existing inference function
        return runModelB(vector)
    }








}
