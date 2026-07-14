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
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import org.tensorflow.lite.Interpreter


private const val MODEL_INPUT_DIM = 65



class OnnxModelRunner(private val context: Context) {

    private val env = OrtEnvironment.getEnvironment()
    private val sessionA: OrtSession



    private lateinit var deepSessionInterpreter: Interpreter


    init {
        sessionA = env.createSession(assetFilePath("modelA.onnx"))
        deepSessionInterpreter = Interpreter(loadMappedFile("deep_session_multimodel.tflite"))

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

    fun runModelA(text: String): ModelAResult {
        val input = arrayOf(arrayOf(text))
        val tensor = OnnxTensor.createTensor(env, input)

        val result = sessionA.run(mapOf("input" to tensor))

        // 1. Extract label (String array → take first element)
        val labelArray = result[0].value as Array<String>
        val label = labelArray[0]

        // 2. Extract probability map (sequence of OnnxMap)
        val probSequence = result[1].value as List<*>

        // First element is an OnnxMap
        val onnxMap = probSequence[0] as ai.onnxruntime.OnnxMap

        // Extract underlying Java Map<String, Float>
        @Suppress("UNCHECKED_CAST")
        val probMap = onnxMap.value as Map<String, Float>

        // Convert map values to a FloatArray
        val probabilities = probMap.values.toFloatArray()

        // Find max probability
        val maxProb = probabilities.maxOrNull() ?: 0f

        return ModelAResult(
            label = label,
            confidence = maxProb,
            probabilities = probabilities
        )
    }

    data class ModelAResult(
        val label: String,
        val confidence: Float,
        val probabilities: FloatArray
    )


    private fun loadMappedFile(filename: String): MappedByteBuffer {
        val afd = context.assets.openFd(filename)
        val inputStream = FileInputStream(afd.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
    }


    fun runDeepSession(inputVector: FloatArray): DeepSessionResult {
        // Defensive checks
        if (!::deepSessionInterpreter.isInitialized) {
            throw IllegalStateException("TFLite interpreter not loaded")
        }

        // Ensure MODEL_INPUT_DIM is defined and matches training input length
        if (inputVector.size != MODEL_INPUT_DIM) {
            Log.e("DeepModel", "Input length ${inputVector.size} != expected $MODEL_INPUT_DIM")
            throw IllegalArgumentException("Model input dimension mismatch")
        }

        // Convert inputVector -> ByteBuffer (float32, native order)
        val inputBuffer = ByteBuffer.allocateDirect(inputVector.size * 4)
            .order(ByteOrder.nativeOrder())
        for (f in inputVector) inputBuffer.putFloat(f)
        inputBuffer.rewind()

        // Inspect outputs and allocate containers dynamically
        val outputCount = deepSessionInterpreter.outputTensorCount
        val outputMap = HashMap<Int, Any>()
        val outputMeta = mutableListOf<Pair<Int, String>>() // (index, name)

        for (i in 0 until outputCount) {
            try {
                val t = deepSessionInterpreter.getOutputTensor(i)
                val name = try { t.name() } catch (_: Exception) { "output_$i" }
                val shape = t.shape()
                val length = shape.fold(1) { acc, dim -> acc * dim }
                val container: Any = when (t.dataType()) {
                    org.tensorflow.lite.DataType.FLOAT32 -> FloatArray(length)
                    org.tensorflow.lite.DataType.INT32 -> IntArray(length)
                    else -> FloatArray(length)
                }
                outputMap[i] = container
                outputMeta.add(Pair(i, name))
                Log.d("DeepModel", "Output[$i] name=$name shape=${shape.contentToString()} dtype=${t.dataType()}")
            } catch (e: Exception) {
                Log.w("DeepModel", "Unable to inspect/allocate output tensor $i: ${e.message}")
            }
        }

        // Run inference using the ByteBuffer as the single input
        try {
            deepSessionInterpreter.runForMultipleInputsOutputs(arrayOf<Any>(inputBuffer), outputMap)
        } catch (e: Exception) {
            Log.e("DeepModel", "Inference failed: ${e.message}")
            throw e
        }

        // Heuristics to map outputs to phq9, gad7, achaRisk, generalDiagnosis, emotionVector
        var phq9Pred = 0f
        var gad7Pred = 0f
        var achaRisk = 0f
        var generalDiagnosis = -1
        var emotionVector: FloatArray = FloatArray(0)

        // Name-based mapping
        for ((index, name) in outputMeta) {
            val container = outputMap[index]
            val lname = name.lowercase()
            when {
                "phq" in lname || "phq9" in lname -> {
                    if (container is FloatArray && container.isNotEmpty()) phq9Pred = container[0]
                }
                "gad" in lname || "gad7" in lname -> {
                    if (container is FloatArray && container.isNotEmpty()) gad7Pred = container[0]
                }
                "acha" in lname || "risk" in lname -> {
                    if (container is FloatArray && container.isNotEmpty()) achaRisk = container[0]
                }
                "general" in lname || "diagnos" in lname -> {
                    if (container is IntArray && container.isNotEmpty()) generalDiagnosis = container[0]
                    else if (container is FloatArray && container.isNotEmpty()) generalDiagnosis = container[0].toInt()
                }
                "emotion" in lname || "emo" in lname || "emotion_vector" in lname -> {
                    if (container is FloatArray) emotionVector = container.copyOf()
                }
            }
        }

        // Shape-based fallback mapping if name-based mapping didn't find everything
        if (phq9Pred == 0f || gad7Pred == 0f || emotionVector.isEmpty()) {
            val floatOutputs = outputMap.values.filterIsInstance<FloatArray>()
            val singleValueOutputs = floatOutputs.filter { it.size == 1 }
            val multiValueOutputs = floatOutputs.filter { it.size > 1 }

            var singleIndex = 0
            if (phq9Pred == 0f && singleIndex < singleValueOutputs.size) {
                phq9Pred = singleValueOutputs[singleIndex][0]; singleIndex++
            }
            if (gad7Pred == 0f && singleIndex < singleValueOutputs.size) {
                gad7Pred = singleValueOutputs[singleIndex][0]; singleIndex++
            }
            if (emotionVector.isEmpty() && multiValueOutputs.isNotEmpty()) {
                emotionVector = multiValueOutputs.maxByOrNull { it.size }?.copyOf() ?: FloatArray(0)
            }
            if (generalDiagnosis == -1) {
                val intOutputs = outputMap.values.filterIsInstance<IntArray>()
                if (intOutputs.isNotEmpty()) generalDiagnosis = intOutputs.first()[0]
            }
        }

        Log.d("DeepModel", "Mapped outputs -> phq9=$phq9Pred gad7=$gad7Pred acha=$achaRisk general=$generalDiagnosis emotionLen=${emotionVector.size}")

        return DeepSessionResult(
            phq9 = phq9Pred,
            gad7 = gad7Pred,
            achaRisk = achaRisk,
            generalDiagnosis = generalDiagnosis,
            emotionVector = emotionVector
        )
    }



    fun logAllModelInputs() {
        // Log Model A inputs
        Log.e("ModelInputs", "===== MODEL A INPUTS =====")
        for ((name, info) in sessionA.inputInfo) {
            val t = info.info as TensorInfo
            Log.e("ModelA", "Input name: $name")
            Log.e("ModelA", "  Type: ${t.type}")
            Log.e("ModelA", "  Shape: ${t.shape.contentToString()}")
        }

        // Log deep TFLite interpreter outputs if loaded
        if (::deepSessionInterpreter.isInitialized) {
            Log.e("ModelInputs", "===== DEEP MODEL OUTPUTS =====")
            val outCount = deepSessionInterpreter.outputTensorCount
            for (i in 0 until outCount) {
                try {
                    val t = deepSessionInterpreter.getOutputTensor(i)
                    val name = try { t.name() } catch (_: Exception) { "output_$i" }
                    Log.e("DeepModel", "Output[$i] name=$name shape=${t.shape().contentToString()} dtype=${t.dataType()}")
                } catch (e: Exception) {
                    Log.w("DeepModel", "Unable to inspect output tensor $i: ${e.message}")
                }
            }
        } else {
            Log.e("ModelInputs", "Deep model interpreter not loaded")
        }
    }


















}
