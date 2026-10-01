package com.example.ui.simulator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SmsSimulatorDialog(
    onDismiss: () -> Unit,
    onSimulateIncomingSms: (sender: String, message: String) -> Unit
) {
    var sender by remember { mutableStateOf("+1 (555) 789-0123") }
    var messageText by remember { mutableStateOf("Hey! This is a test SMS message received on Salim.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Simulate Inbound SMS",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Useful for testing incoming text reception, notifications, and thread sorting on emulators without a physical SIM card.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = sender,
                    onValueChange = { sender = it },
                    label = { Text("Sender Phone or Alpha ID") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sim_sender_input")
                )

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("Message Body") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sim_body_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            sender = "Uber Code"
                            messageText = "Your Uber code is 5192. Never share this code."
                        }
                    ) {
                        Text("OTP Sample", fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = {
                            sender = "+1 (800) 555-SPAM"
                            messageText = "CONGRATS! You won a $1,000 gift card! Claim now at free-spam.example"
                        }
                    ) {
                        Text("Spam Sample", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (sender.isNotBlank() && messageText.isNotBlank()) {
                        onSimulateIncomingSms(sender.trim(), messageText.trim())
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("sim_send_button")
            ) {
                Text("Receive SMS")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
