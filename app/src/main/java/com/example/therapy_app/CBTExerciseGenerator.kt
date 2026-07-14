package com.example.therapy_app

import android.util.Log
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.example.therapy_app.fetchWebsiteText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import android.content.Context


class MultiTherapyExerciseGenerator {

    // Public entry point
    suspend fun generateExercises(
        therapyType: String,
        disorder: String,
        symptoms: FloatArray,
        userMessage: String,
        treatmentPattern: TreatmentPattern,
        client: OpenAI,
        dynamicGenerator: suspend (String) -> String,
        deepManager: DeepSessionManager,
        context: Context
    ): List<String> {

        Log.d("DBT_DEBUG", "Generator received therapyType = $therapyType | normalized = ${therapyType.uppercase()}")

        when (therapyType.uppercase()) {

            "DBT" -> {
                Log.d("DBT_DEBUG", "➡ DBT ROUTE ACTIVATED")
                val dbtExercises = generateDbtWebsiteExercises(client, symptoms, context)
                deepManager.generatedDbtExercises = dbtExercises // ⭐ cache full objects
                return dbtExercises.map { it.name }             // ⭐ return names to UI
            }

            else -> {
                Log.d("DBT_DEBUG", "➡ NON-DBT ROUTE: ${therapyType.uppercase()}")

                val dynamicNames = try {
                    val prompt = buildDynamicPrompt(
                        disorder = disorder,
                        symptoms = symptoms,
                        userMessage = userMessage,
                        treatmentPattern = treatmentPattern
                    )

                    val raw = dynamicGenerator(prompt)
                    parseDynamicResponse(raw)

                } catch (e: Exception) {
                    emptyList<String>()
                }

                // Choose up to 3 names from dynamic or fallback
                val namesToUse = if (dynamicNames.isNotEmpty()) {
                    dynamicNames.take(3)
                } else {
                    generateStaticFallbackExercises(disorder, symptoms).take(3)
                }

                // Convert names -> full DbtExercise objects (replace placeholder steps with real generation if available)
                val generatedExercises = namesToUse.map { name ->
                    DbtExercise(
                        name = name,
                        url = "https://example.com/exercises/${name.replace(" ", "-").lowercase()}",
                        description = "Exercise for $name — brief description placeholder.",
                        steps = listOf(
                            "Step 1 for $name",
                            "Step 2 for $name",
                            "Step 3 for $name"
                        )
                    )
                }


                // Cache full objects so downstream code can call beginExercise / generateExerciseStep
                deepManager.generatedDbtExercises = generatedExercises

                // Return names for UI
                return generatedExercises.map { it.name }
            }
        }
    }


    // ---------------------------------------------------------
    // Dynamic prompt (LLM side)
    // ---------------------------------------------------------
    private fun buildDynamicPrompt(
        disorder: String,
        symptoms: FloatArray,
        userMessage: String,
        treatmentPattern: TreatmentPattern
    ): String {

        val cluster = treatmentPattern.cluster
        val focus = treatmentPattern.focus
        val methods = treatmentPattern.methods.joinToString()

        return """
           You are a warm, evidence‑based mental health support assistant.

           Your task is to generate **3 short therapeutic exercises** based on the user's needs.

           USER CONTEXT:
           - Predicted difficulty / disorder: $disorder
           - Symptom vector (0/1 flags): ${symptoms.joinToString()}
           - User's recent message: "$userMessage"

           TREATMENT PATTERN (from Model C):
           - Treatment style cluster: $cluster
           - Focus area: $focus
           - Recommended methods: $methods

           THERAPY REQUIREMENTS:
           - Choose the therapy style that best fits the pattern (e.g., CBT, DBT, Interpersonal Therapy, Mindfulness-Based Therapy, Psychodynamic Therapy, Humanistic Therapy, ACT, CFT).
           - Generate 3 short exercises aligned with that therapy style.
           - Each exercise must be 1–2 sentences.
           - Use simple, everyday language.
           - Exercises must be specific, actionable, and supportive.
           - Avoid overwhelming the user.

           Return them as a numbered list:
           1) ...
           2) ...
           3) ...
        """.trimIndent()
    }

    private fun parseDynamicResponse(raw: String): List<String> {
        return raw
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                line.replace(Regex("""^\d+[\).\-\s]+"""), "")
            }
            .filter { it.isNotBlank() }
    }

    // ---------------------------------------------------------
    // Static fallback (therapy‑agnostic)
    // ---------------------------------------------------------
    private fun generateStaticFallbackExercises(
        disorder: String,
        symptoms: FloatArray
    ): List<String> {

        val list = mutableListOf<String>()

        // Emotion regulation fallback
        if (disorder.contains("anxiety", ignoreCase = true)) {
            list += "Try a grounding exercise: name 5 things you see, 4 you touch, 3 you hear, 2 you smell, 1 you taste."
        }

        // Behavioural activation fallback
        if (disorder.contains("depression", ignoreCase = true)) {
            list += "Choose one very small task and complete just the first step today."
        }

        // Behaviour awareness fallback
        if (symptoms.any { it == 1f }) {
            list += "Notice one behaviour today that felt unhelpful and write down what triggered it."
        }

        // General fallback
        if (list.isEmpty()) {
            list += "Take 2 minutes to slow your breathing and write down one thing you want to focus on today."
        }

        return list
    }

    private suspend fun selectDbtExercises(
        exercises: List<DbtExercise>,
        symptoms: FloatArray,
        client: OpenAI
    ): List<DbtExercise> {

        val exerciseListText = exercises.joinToString("\n") { ex ->
            "${ex.name} — ${ex.description} — ${ex.url}\nSTEPS:\n" +
                    ex.steps.joinToString("\n") { "- $it" }
        }

        val selectionPrompt = """
You are selecting DBT exercises for a user based on their symptoms.

RULES:
- Select ONLY from the list provided.
- Use the EXACT exercise names.
- Choose 2–3 exercises that best match the user's symptoms.
- Provide output in this format:

1) Exercise Name
2) Exercise Name
3) Exercise Name

EXERCISES:
$exerciseListText

USER SYMPTOMS:
${symptoms.joinToString()}
""".trimIndent()

        val response = client.chatCompletion(
            ChatCompletionRequest(
                model = ModelId("gpt-4o-mini"),
                messages = listOf(ChatMessage(ChatRole.User, selectionPrompt))
            )
        )

        val rawLines = response.choices.first().message?.content?.lines() ?: emptyList()

        val cleanedNames = rawLines.mapNotNull { line ->
            val name = line.substringAfter(") ").trim()

            // Remove trailing reasons if present
            name.substringBefore(" —")
                .substringBefore(" -")
                .substringBefore(":")
                .substringBefore("(")
                .trim()
                .takeIf { it.isNotBlank() }
        }

        return exercises.filter { it.name in cleanedNames }
    }



    private suspend fun generateDbtWebsiteExercises(
        client: OpenAI,
        symptoms: FloatArray,
        context: Context
    ): List<DbtExercise>     = coroutineScope {

        val urls = listOf(
            "https://dbtselfhelp.com/distress-tolerance/tipp/",
            "https://dbtselfhelp.com/distress-tolerance/stop/",
            "https://dbtselfhelp.com/distress-tolerance/radical-acceptance/",
            "https://dbtselfhelp.com/distress-tolerance/self-soothe/",
            "https://dbtselfhelp.com/distress-tolerance/improve-the-moment/",
            "https://dbtselfhelp.com/distress-tolerance/pros-and-cons/",
            "https://dbtselfhelp.com/emotion-regulation/opposite-action/",
            "https://dbtselfhelp.com/emotion-regulation/check-the-facts/",
            "https://dbtselfhelp.com/emotion-regulation/abc-please/",
            "https://dbtselfhelp.com/interpersonal-effectiveness/dear-man/",
            "https://dbtselfhelp.com/interpersonal-effectiveness/give/",
            "https://dbtselfhelp.com/interpersonal-effectiveness/fast/",
            "https://dbtselfhelp.com/mindfulness/wise-mind/"
        )

        // ⭐ Fetch all pages in parallel
        val pages: List<String> = urls.map { url ->
            async(Dispatchers.IO) {
                Log.d("DBT_DEBUG", "Fetching page: $url")
                fetchWebsiteText(url)
            }
        }.awaitAll()

        // ⭐ Combine into one raw text blob
        val raw = pages.joinToString(separator = "\n\n")

        Log.d("DBT_DEBUG", "RAW WEBSITE CONTENT (first 500 chars):\n${raw.take(500)}")

        // ⭐ Summarise website into structured exercises
        val summary = summariseDbtExercises(raw, client, context)

        Log.d("DBT_DEBUG", "DBT SUMMARY OUTPUT:\n$summary")

        if (!summary.contains("http")) {
            Log.e("DBT_DEBUG", "SUMMARY MISSING URLS — summariser prompt must be updated")
        }

        // ⭐ Parse all exercises from the summary
        val allExercises = parseDbtExercises(summary)

        Log.d("DBT_DEBUG", "Parsed ${allExercises.size} exercises")
        allExercises.forEach {
            Log.d("DBT_DEBUG", "Exercise: ${it.name}, steps=${it.steps.size}")
        }


        // ⭐ Select exercises based on symptoms
        val selectedExercises = selectDbtExercises(
            exercises = allExercises,
            symptoms = symptoms,
            client = client
        )

        selectedExercises
    }




    private fun parseDbtExercises(summary: String): List<DbtExercise> {
        val blocks = summary.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }

        return blocks.mapNotNull { block ->
            val lines = block.lines().map { it.trim() }

            val header = lines.firstOrNull() ?: return@mapNotNull null
            val parts = header.split(" — ")

            if (parts.size < 3) return@mapNotNull null

            val name = parts[0]
            val description = parts[1]
            val url = parts[2]

            // ⭐ Find the index of "STEPS:" line
            val stepsIndex = lines.indexOfFirst { it.equals("STEPS:", ignoreCase = true) }

            val steps =
                if (stepsIndex != -1)
                    lines
                        .drop(stepsIndex + 1)          // skip "STEPS:"
                        .filter { it.startsWith("- ") } // only step lines
                        .map { it.removePrefix("- ").trim() }
                else
                    emptyList()

            DbtExercise(
                name = name,
                description = description,
                url = url,
                steps = steps
            )
        }
    }




}



