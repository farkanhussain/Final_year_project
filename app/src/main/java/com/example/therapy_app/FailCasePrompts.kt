package com.example.therapy_app

object FailCasePrompts {

    fun offerPause(): String =
        "Thanks for sharing that. It makes sense this step didn’t shift things yet. We can pause here if you’d like."

    fun grounding(): String =
        "I’m really glad you told me. Let’s pause the exercise and take a grounding moment — just notice your breath for a few seconds."

    fun mismatch(): String =
        "It sounds like this exercise isn’t fitting well. We can switch to a different skill if you prefer."

    fun fatigue(): String =
        "It’s okay to stop here. Let’s take a moment to settle before moving on."
}
