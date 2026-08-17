package com.jk.offermate.agent

/**
 * 测试用的假 AiClient：按传入的 responder 返回预置（录制）响应，并记录收到的消息，便于断言 Prompt。
 */
class FakeAiClient(
    private val responder: (List<ChatMessage>) -> String
) : AiClient {

    val recordedMessages = mutableListOf<List<ChatMessage>>()

    override suspend fun chat(messages: List<ChatMessage>): String {
        recordedMessages += messages
        return responder(messages)
    }

    /** 最近一次调用时发送的消息。 */
    val lastMessages: List<ChatMessage>
        get() = recordedMessages.last()

    companion object {
        /** 恒定返回同一段文本。 */
        fun returning(response: String): FakeAiClient = FakeAiClient { response }
    }
}
