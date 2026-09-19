package com.jk.offermate.ui.followup

import com.jk.offermate.agent.ChatMessage
import com.jk.offermate.agent.Role
import com.jk.offermate.ui.components.StreamingMarkdown

/**
 * 聊天列表的一行（= 一个 LazyColumn item）。
 *
 * 为什么要有这一层 —— 原来「一条消息一个 item」，一条长回答就是一个包含几百个 layout node 的巨型
 * item。LazyColumn 必须在它进入视口的那一帧把整棵树组合完、测量完，长回答必然掉帧，
 * 表现为滚动到消息交界处卡一下。
 *
 * 拆成「AI 消息按 Markdown 块展开成多个 item」之后，只有真正可见的块会被组合和测量。
 *
 * 代价是 **item 下标不再等于消息下标**，所有滚动定位都要经 [ChatRows.firstRowOfMessage] 换算。
 */
internal sealed interface ChatRow {

    /** 该行属于哪条消息（messages 列表中的下标）。 */
    val messageIndex: Int

    /** 是否是该消息的第一行——用于补上消息之间的间距。 */
    val isMessageStart: Boolean

    /** LazyColumn 的稳定 key。消息只会追加不会插入，所以「消息下标 + 块序号」足够稳定。 */
    val key: String

    /**
     * LazyColumn 的复用类型。滚动时同类型 item 之间可以复用组合槽位——更新一棵已有的组合树，
     * 而不是从零建一棵。用户气泡和 Markdown 块的结构完全不同，混在一起复用没有意义，
     * 所以显式区分开；不给的话所有 item 都是同一类型（null），会互相复用到无效。
     */
    val contentType: String

    /** 用户消息：整条一个 item（本来就短，没有拆分价值）。 */
    data class User(
        override val messageIndex: Int,
        override val isMessageStart: Boolean,
        val content: String
    ) : ChatRow {
        override val key: String get() = "u$messageIndex"
        override val contentType: String get() = "user"
    }

    /** AI 回答的一个 Markdown 块。 */
    data class AiBlock(
        override val messageIndex: Int,
        override val isMessageStart: Boolean,
        val blockIndex: Int,
        val text: String
    ) : ChatRow {
        override val key: String get() = "a$messageIndex-$blockIndex"
        override val contentType: String get() = "ai"
    }
}

/**
 * 展开后的行列表 + 「消息下标 → 该消息首行下标」的映射。
 *
 * @property firstRowOfMessage 下标与 messages 一一对应；内容为空的消息指向其后一行的位置。
 */
internal class ChatRows(
    val rows: List<ChatRow>,
    private val firstRowOfMessage: IntArray
) {
    /** 把「消息下标」换算成「item 下标」，用于搜索跳转定位。越界会被夹到合法范围。 */
    fun rowOfMessage(messageIndex: Int): Int {
        if (firstRowOfMessage.isEmpty()) return 0
        val mi = messageIndex.coerceIn(0, firstRowOfMessage.size - 1)
        return firstRowOfMessage[mi].coerceIn(0, maxOf(rows.lastIndex, 0))
    }
}

/**
 * 把**已落库**的消息列表展开成行列表。
 *
 * 不再接收流式文本：正在生成的那条消息由页面单独渲染（见 `FollowUpScreen` 里的流式尾行），
 * 否则每个出字节拍都要重建这整个列表，而列表里上百行里只有一行真的变了。
 */
internal fun buildChatRows(messages: List<ChatMessage>): ChatRows {
    val rows = ArrayList<ChatRow>(messages.size * 4)
    val firstRowOfMessage = IntArray(messages.size)

    messages.forEachIndexed { messageIndex, message ->
        firstRowOfMessage[messageIndex] = rows.size
        if (message.role == Role.USER) {
            rows.add(ChatRow.User(messageIndex, isMessageStart = true, content = message.content))
            return@forEachIndexed
        }

        if (message.content.isEmpty()) return@forEachIndexed

        var emitted = 0
        StreamingMarkdown.blocksMemo(message.content).forEachIndexed { blockIndex, block ->
            if (block.isEmpty()) return@forEachIndexed
            rows.add(
                ChatRow.AiBlock(
                    messageIndex = messageIndex,
                    isMessageStart = emitted == 0,
                    blockIndex = blockIndex,
                    text = block
                )
            )
            emitted++
        }
    }
    return ChatRows(rows, firstRowOfMessage)
}
