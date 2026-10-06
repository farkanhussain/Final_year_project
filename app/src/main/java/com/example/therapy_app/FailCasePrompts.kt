package com.example.therapy_app

object FailCasePrompts {

    fun offerPause(): String =
        "Thanks for sharing that. It makes sense this step didn’t shift things yet. We can pause here if you’d like — you can continue the exercise, switch to a different one, or finish the session."


    fun grounding(): String =
        "I’m really glad you told me. Let’s pause the exercise and take a grounding moment — just notice your breath for a few seconds. When you're ready, you can continue, switch exercises, or finish the session."

    fun mismatch(): String =
        "It sounds like this exercise isn’t fitting well. We can pause here — you can continue if you'd like, switch to a different exercise, or finish the session."


    fun fatigue(): String =
        "It’s okay to stop here. Let’s take a moment to settle before moving on — you can continue the exercise, switch to another one, or finish the session whenever you're ready."

}
