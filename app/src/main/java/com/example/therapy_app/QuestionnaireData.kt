package com.example.therapy_app

object QuestionnaireData {

    val phq9 = listOf(
        "Little interest or pleasure in doing things",
        "Feeling down, depressed, or hopeless",
        "Trouble falling or staying asleep, or sleeping too much",
        "Feeling tired or having little energy",
        "Poor appetite or overeating",
        "Feeling bad about yourself — or that you are a failure",
        "Trouble concentrating on things",
        "Moving or speaking slowly — or being fidgety/restless",
        "Thoughts that you would be better off dead"
    )

    val gad7 = listOf(
        "Feeling nervous, anxious, or on edge",
        "Not being able to stop or control worrying",
        "Worrying too much about different things",
        "Trouble relaxing",
        "Being so restless that it is hard to sit still",
        "Becoming easily annoyed or irritable",
        "Feeling afraid as if something awful might happen"
    )

    // ---------------------------------------------------------
    // ACCHA Depression (12‑month burden + diagnosis history)
    // ---------------------------------------------------------
    val achaDepressionFrequency = listOf(
        "Within the last 12 months, how often have you felt things were hopeless?",
        "Within the last 12 months, how often have you felt overwhelmed by all you had to do?",
        "Within the last 12 months, how often have you felt exhausted (not from physical activity)?",
        "Within the last 12 months, how often have you felt very sad?",
        "Within the last 12 months, how often have you felt so depressed that it was difficult to function?",
        "Within the last 12 months, have you seriously considered attempting suicide?",
        "Within the last 12 months, have you attempted suicide?"
    )

    val achaDepressionDiagnosis = listOf(
        "Have you ever been diagnosed with depression?",
        "If yes, were you diagnosed within the last 12 months?",
        "If yes, are you currently in therapy for depression?",
        "If yes, are you currently taking medication for depression?"
    )
}


