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

        // Read model input tensor shape and dtype at runtime
        val inputTensor = deepSessionInterpreter.getInputTensor(0)
        val shape = inputTensor.shape() // e.g. [1, 66] or [1, H, W, C]
        val dtype = inputTensor.dataType() // FLOAT32, UINT8, etc.

        val numElements = shape.fold(1) { acc, v -> acc * v }
        val bytesPerElement = when (dtype) {
            org.tensorflow.lite.DataType.FLOAT32 -> 4
            org.tensorflow.lite.DataType.UINT8 -> 1
            org.tensorflow.lite.DataType.INT32 -> 4
            else -> 4
        }
        val expectedBytes = numElements * bytesPerElement

        Log.d(
            "DeepModel",
            "Model input shape=${shape.contentToString()} dtype=$dtype numElements=$numElements expectedBytes=$expectedBytes"
        )
        Log.d("DeepModel", "Provided inputVector.size=${inputVector.size}")

        // Validate inputVector length against model elements
        if (inputVector.size != numElements) {
            Log.e(
                "DeepModel",
                "Input length ${inputVector.size} does not match model elements $numElements"
            )
            throw IllegalArgumentException("Model input dimension mismatch: expected $numElements elements, got ${inputVector.size}")
        }

        // Convert inputVector -> ByteBuffer using model expected size and dtype
        val inputBuffer = ByteBuffer.allocateDirect(expectedBytes).order(ByteOrder.nativeOrder())
        if (dtype == org.tensorflow.lite.DataType.FLOAT32) {
            for (f in inputVector) inputBuffer.putFloat(f)
        } else if (dtype == org.tensorflow.lite.DataType.UINT8) {
            // Example quantization if your model expects UINT8; adjust scaling as needed
            for (f in inputVector) inputBuffer.put((f.toInt() and 0xFF).toByte())
        } else {
            for (f in inputVector) inputBuffer.putFloat(f)
        }
        inputBuffer.rewind()
        Log.d(
            "DeepModel",
            "Input buffer capacity=${inputBuffer.capacity()} remaining=${inputBuffer.remaining()}"
        )

        // Inspect outputs and allocate containers dynamically
        val outputCount = deepSessionInterpreter.outputTensorCount
        val outputMap = HashMap<Int, Any>()
        val outputMeta = mutableListOf<Pair<Int, String>>() // (index, name)

        for (i in 0 until outputCount) {
            try {
                val t = deepSessionInterpreter.getOutputTensor(i)
                val name = try {
                    t.name()
                } catch (_: Exception) {
                    "output_$i"
                }
                val shapeOut = t.shape() // e.g. [1,1] or [1,10]

                // Allocate EXACT shape: Array(batch) { FloatArray(features) }
                val container: Any = when (t.dataType()) {
                    org.tensorflow.lite.DataType.FLOAT32 -> {
                        Array(shapeOut[0]) { FloatArray(shapeOut[1]) }
                    }

                    org.tensorflow.lite.DataType.INT32 -> {
                        Array(shapeOut[0]) { IntArray(shapeOut[1]) }
                    }

                    else -> {
                        Array(shapeOut[0]) { FloatArray(shapeOut[1]) }
                    }
                }

                outputMap[i] = container
                outputMeta.add(Pair(i, name))

                Log.d(
                    "DeepModel",
                    "Output[$i] name=$name shape=${shapeOut.contentToString()} dtype=${t.dataType()}"
                )
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
                    if (container is Array<*> &&
                        container.isNotEmpty() &&
                        container[0] is FloatArray &&
                        (container[0] as FloatArray).isNotEmpty()
                    ) {
                        phq9Pred = (container[0] as FloatArray)[0]
                    }
                }

                "gad" in lname || "gad7" in lname -> {
                    if (container is Array<*> &&
                        container.isNotEmpty() &&
                        container[0] is FloatArray &&
                        (container[0] as FloatArray).isNotEmpty()
                    ) {
                        gad7Pred = (container[0] as FloatArray)[0]
                    }
                }

                "acha" in lname || "risk" in lname -> {
                    if (container is Array<*> &&
                        container.isNotEmpty() &&
                        container[0] is FloatArray &&
                        (container[0] as FloatArray).isNotEmpty()
                    ) {
                        achaRisk = (container[0] as FloatArray)[0]
                    }
                }

                "general" in lname || "diagnos" in lname -> {
                    if (container is Array<*> &&
                        container.isNotEmpty()
                    ) {
                        when (val row = container[0]) {
                            is IntArray -> if (row.isNotEmpty()) generalDiagnosis = row[0]
                            is FloatArray -> if (row.isNotEmpty()) generalDiagnosis = row[0].toInt()
                        }
                    }
                }

                "emotion" in lname || "emo" in lname || "emotion_vector" in lname -> {
                    if (container is Array<*> &&
                        container.isNotEmpty() &&
                        container[0] is FloatArray
                    ) {
                        emotionVector = (container[0] as FloatArray).copyOf()
                    }
                }
            }
        }

        // Shape-based fallback mapping if name-based mapping didn't find everything
        if (phq9Pred == 0f || gad7Pred == 0f || emotionVector.isEmpty()) {
            // Flatten all Array<FloatArray> into plain FloatArray lists
            val floatOutputs = outputMap.values
                .filterIsInstance<Array<FloatArray>>()
                .flatMap { it.toList() }

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
                val intOutputs = outputMap.values
                    .filterIsInstance<Array<IntArray>>()
                    .flatMap { it.toList() }
                if (intOutputs.isNotEmpty() && intOutputs[0].isNotEmpty()) {
                    generalDiagnosis = intOutputs[0][0]
                }
            }
        }

        Log.d(
            "DeepModel",
            "Mapped outputs -> phq9=$phq9Pred gad7=$gad7Pred acha=$achaRisk general=$generalDiagnosis emotionLen=${emotionVector.size}"
        )

        return DeepSessionResult(
            phq9 = phq9Pred,
            gad7 = gad7Pred,
            achaRisk = achaRisk,
            generalDiagnosis = generalDiagnosis,
            emotionVector = emotionVector
        )
    }

        // Allocate a nested Java array matching `shape` for FLOAT32 or INT32
    fun allocateOutputContainer(shape: IntArray, dtype: org.tensorflow.lite.DataType): Any {
        // If rank == 1, return a simple primitive array
        if (shape.size == 1) {
            val len = shape[0]
            return when (dtype) {
                org.tensorflow.lite.DataType.FLOAT32 -> FloatArray(len)
                org.tensorflow.lite.DataType.INT32 -> IntArray(len)
                else -> FloatArray(len)
            }
        }

        // For rank >= 2, build nested arrays. This builds arrays of depth = rank.
        // Example: shape=[1,1] -> Array(1) { FloatArray(1) }
        fun build(level: Int): Any {
            val dim = shape[level]
            if (level == shape.lastIndex) {
                return when (dtype) {
                    org.tensorflow.lite.DataType.FLOAT32 -> FloatArray(dim)
                    org.tensorflow.lite.DataType.INT32 -> IntArray(dim)
                    else -> FloatArray(dim)
                }
            } else {
                val arr = java.lang.reflect.Array.newInstance(
                    build(level + 1)::class.java, dim
                )
                for (i in 0 until dim) {
                    java.lang.reflect.Array.set(arr, i, build(level + 1))
                }
                return arr
            }
        }
        return build(0)
    }

    // Flatten nested output container into a 1D FloatArray
    fun flattenFloatOutput(container: Any, shape: IntArray): FloatArray {
        val total = shape.fold(1) { acc, v -> acc * v }
        val out = FloatArray(total)
        var idx = 0
        fun recurse(obj: Any) {
            when (obj) {
                is FloatArray -> {
                    for (v in obj) { out[idx++] = v }
                }
                is Array<*> -> {
                    for (el in obj) { if (el != null) recurse(el) }
                }
                else -> {
                    // handle other nested primitive arrays if needed
                }
            }
        }
        recurse(container)
        return out
    }

    // Flatten nested output container into a 1D IntArray
    fun flattenIntOutput(container: Any, shape: IntArray): IntArray {
        val total = shape.fold(1) { acc, v -> acc * v }
        val out = IntArray(total)
        var idx = 0
        fun recurse(obj: Any) {
            when (obj) {
                is IntArray -> {
                    for (v in obj) { out[idx++] = v }
                }
                is Array<*> -> {
                    for (el in obj) { if (el != null) recurse(el) }
                }
            }
        }
        recurse(container)
        return out
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
