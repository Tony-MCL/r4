package com.morningcoffeelabs.r4

data class Message(
    val id: String,
    val title: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
)
