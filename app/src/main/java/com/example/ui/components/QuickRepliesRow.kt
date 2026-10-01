package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QuickRepliesRow(
    onSelectReply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val quickReplies = listOf(
        "Sounds good! 👍",
        "I'm on my way 🚗",
        "Can I call you in 5?",
        "Thanks so much!",
        "Yes, absolutely",
        "Let's catch up later"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("quick_replies_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        quickReplies.forEachIndexed { index, reply ->
            Surface(
                onClick = { onSelectReply(reply) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.testTag("quick_reply_$index")
            ) {
                Text(
                    text = reply,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
