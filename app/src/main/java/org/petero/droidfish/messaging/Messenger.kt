package org.petero.droidfish.messaging

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.azure.core.amqp.AmqpTransportType
import com.azure.messaging.servicebus.ServiceBusClientBuilder
import com.azure.messaging.servicebus.ServiceBusErrorContext
import com.azure.messaging.servicebus.ServiceBusMessage
import com.azure.messaging.servicebus.ServiceBusProcessorClient
import com.azure.messaging.servicebus.ServiceBusSenderClient
import java.util.Base64

typealias MessageHandler = (String) -> Unit

class Messenger {
    companion object MessagingConstants {
        const val TAG = "Messenger"
        private const val AZURE_SERVICE_BUS_CONNECTION_STRING_IN_BASE64 =
            "RW5kcG9pbnQ9c2I6Ly9hbGxvZnVzLWNoZWNrbWF0ZS5zZXJ2aWNlYnVzLndpbmRvd3MubmV0LztTaGFyZWRBY2Nlc3NLZXlOYW1lPVJvb3RNYW5hZ2VTaGFyZWRBY2Nlc3NLZXk7U2hhcmVkQWNjZXNzS2V5PVNKUndUN1lNTWJHSkVSaEhuZ2grRkdBVDhJaFF2amUyTytBU2JJZXZBTGs9"
        const val CLIENT_QUEUE_NAME = "client"
        const val HANDLER_QUEUE_NAME = "handler"

        fun decodedAzureServiceBusConnectionString(): String {
            val decodedBytes = Base64.getDecoder().decode(AZURE_SERVICE_BUS_CONNECTION_STRING_IN_BASE64)
            return String(decodedBytes)
        }
    }

    var onNewMessage: MessageHandler = {}

    private val clientBuilder = ServiceBusClientBuilder()
        .connectionString(decodedAzureServiceBusConnectionString())
        .transportType(AmqpTransportType.AMQP_WEB_SOCKETS)

    private val sender: ServiceBusSenderClient = clientBuilder
        .sender()
        .queueName(HANDLER_QUEUE_NAME)
        .buildClient()

    private val processor: ServiceBusProcessorClient = clientBuilder
        .processor()
        .queueName(CLIENT_QUEUE_NAME)
        .processMessage { context ->
            run {
                val message = context.message
                logMessage(
                    "Processing message. Session: ${message.sessionId}, Sequence: ${message.sequenceNumber}"
                )

                val stringBody = message.body.toString()
                logMessage("Processing message $stringBody")
                try {
                    onNewMessage(stringBody)
                } catch (processError: Exception) {
                    logMessage(
                        "Processing of the message {message.messageId} failed: ${processError.message}",
                        processError
                    )
                }

                try {
                    context.complete()
                } catch (completionError: Exception) {
                    logMessage(
                        "Completion of the message {message.messageId} failed: ${completionError.message}",
                        completionError
                    )
                }
            }
        }
        .processError { context -> processError(context) }
        .buildProcessorClient()

    private val messages: ArrayList<String> = ArrayList()

    private fun logMessage(message: String, ex: Exception? = null) {
        Log.d(TAG, message, ex)
    }

    private fun processError(context: ServiceBusErrorContext) {
        logMessage(
            "Error when receiving messages from namespace: '${context.fullyQualifiedNamespace}'. Entity: '${context.entityPath}'"
        )
    }

    fun startProcessingMessages() {
        if (!processor.isRunning)
            processor.start()
    }

    fun addMessage(message: String) {
        messages.add(message)
    }

    fun sendWithMessages(newMessage: String) {
        messages.add(newMessage)
        val joinedMessage = messages.joinToString(".")
        messages.clear()
        send(joinedMessage)
    }

    fun send(message: String) {
        try {
            sender.sendMessage(ServiceBusMessage(message))
            logMessage("Sent message $message")
        } catch (e: java.lang.Exception) {
            logMessage("Error sending message $message. Exception: $e. Retrying 3 seconds later")
            Handler(Looper.getMainLooper()).postDelayed({ send(message) }, 3000)
        }
    }

    fun close() {
        sender.close()
        if (processor.isRunning)
            processor.stop()
        processor.close()
    }
}

