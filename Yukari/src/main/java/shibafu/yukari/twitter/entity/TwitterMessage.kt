package shibafu.yukari.twitter.entity

import shibafu.yukari.entity.Status
import shibafu.yukari.entity.User
import shibafu.yukari.twitter.AuthUserRecord
import twitter4j.DirectMessage
import java.util.*

class TwitterMessage(val message: DirectMessage) : Status {
    override val id: Long
        get() = message.id

    override val user: User by lazy { TwitterUser(message.sender) }

    override val text: String
        get() = message.text

    override val recipientScreenName: String
        get() = message.recipientScreenName

    override val createdAt: Date
        get() = message.createdAt

    override val source: String
        get() = "DirectMessage"

    override fun getStatusRelation(userRecords: List<AuthUserRecord>): Long {
        userRecords.forEach { userRecord ->
            if (userRecord.ScreenName == message.sender.screenName) {
                return Status.RELATION_OWNED
            }
        }
        return Status.RELATION_MENTIONED_TO_ME
    }
}