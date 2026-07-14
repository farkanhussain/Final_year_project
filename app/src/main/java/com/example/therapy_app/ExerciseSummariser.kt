package com.example.therapy_app

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.hashCode



private fun getPrefs(context: Context) =
    context.getSharedPreferences("dbt_cache", Context.MODE_PRIVATE)


suspend fun summariseDbtExercises(
    rawText: String,
    client: OpenAI,
    context: Context
): String {


    val prefs = getPrefs(context)
    val cacheKey = "dbt_summary_cache"
    val timeKey = "dbt_summary_timestamp"

    val now = System.currentTimeMillis()
    val twentyFourHours = 24 * 60 * 60 * 1000L

    val rawHash = rawText.hashCode()

    // ⭐ Check cache
    val cachedSummary = prefs.getString(cacheKey, null)
    val cachedTime = prefs.getLong(timeKey, 0L)
    val cachedHash = prefs.getInt("dbt_raw_hash", -1)

    if (cachedSummary != null &&
        cachedHash == rawText.hashCode() &&
        (now - cachedTime) < twentyFourHours
    ) {
        return cachedSummary
    }

    Log.d("DBT_CACHE", "Cache expired or missing — generating new DBT summary")

    // ⭐ Build prompt
    val prompt = """
You are a strict extraction assistant.

Your ONLY job is to read the full website content below and extract:
- the official DBT SKILL NAME
- a short rewritten description (1–2 sentences)
- the EXACT URL the skill came from
- the EXACT STEP NAMES that appear on the page (in order)

This website contains:
- navigation menus
- category headings (e.g., “Distress Tolerance”, “Emotion Regulation”)
- non‑exercise text
- footer text
IGNORE ALL OF THAT.

HARD REQUIREMENTS (do NOT break these):

1. VALID EXERCISE NAMES
Extract exercise names ONLY from:
- skill headings
- subheadings
- clearly labeled DBT skill titles

Valid DBT skill names include (examples):
“TIPP”, “STOP Skill”, “Opposite Action”, “Check the Facts”, “DEAR MAN”, “FAST”, “GIVE”, “Wise Mind”, 
“IMPROVE the Moment”, “Self‑Soothe”, “Radical Acceptance”, “Pros & Cons”, “ABC PLEASE”.

Preserve exact spelling and formatting.

2. INVALID NAMES (DO NOT EXTRACT)
Do NOT extract:
- category names (“Distress Tolerance”, “Emotion Regulation”, “Mindfulness”, “Interpersonal Effectiveness”)
- menu items
- navigation labels
- footer text
- generic labels (“Mindfulness Meditation”, “Emotion Regulation Skills”, “Distress Tolerance Techniques”)
unless they appear EXACTLY as skill titles.

3. DESCRIPTION RULES
For each exercise:
- Write a short 1–2 sentence description.
- MUST be based ONLY on the website content.
- MUST be rewritten in your own words.
- MUST NOT quote the website.
- MUST NOT invent steps or details.

4. URL RULES
For each exercise:
- Include the EXACT URL where the exercise was found.
- MUST NOT invent or modify URLs.

5. STEP RULES
For each exercise:
- Extract ONLY steps explicitly listed on the page.
- Use EXACT step names.
- Do NOT invent, merge, rename, or reorder steps.
- If the page lists sub‑skills (e.g., Temperature, Intense Exercise, Paced Breathing), extract them.

6. OUTPUT FORMAT (MANDATORY)
Exercise Name — rewritten description — URL
STEPS:
- Step 1
- Step 2
- Step 3
...

Repeat this block for EVERY valid DBT skill found on the website.
Do NOT include headings, commentary, or extra formatting.

WEBSITE CONTENT:
$rawText
""".trimIndent()

    // ⭐ Call OpenAI
    val response = client.chatCompletion(
        ChatCompletionRequest(
            model = ModelId("gpt-4o-mini"),
            messages = listOf(ChatMessage(ChatRole.User, prompt))
        )
    )

    val summary = response.choices.first().message?.content?.trim()
        ?: "Unable to summarise DBT exercises."

    // ⭐ Save to cache
    prefs.edit()
        .putString(cacheKey, summary)
        .putLong(timeKey, now)
        .putInt("dbt_raw_hash", rawHash)
        .apply()


    Log.d("DBT_CACHE", "Saved new DBT summary to cache")

    return summary
}


