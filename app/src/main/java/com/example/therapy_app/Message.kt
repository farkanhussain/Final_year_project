package com.example.therapy_app

data class Message(
    val text: String = "",
    val user: Boolean = false,
    val type: MessageType = MessageType.NORMAL,
    val emoji: String? = null
)

enum class MessageType {
    NORMAL,
    MOOD_CONFIRMATION,

    MOOD_SELECTOR,

    SUMMARY

}
