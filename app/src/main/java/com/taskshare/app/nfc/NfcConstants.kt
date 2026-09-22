package com.taskshare.app.nfc

/** Must match res/xml/apduservice.xml's aid-filter. */
val TASK_SHARE_AID: ByteArray = byteArrayOf(
    0xF0.toByte(), 0x54, 0x61, 0x73, 0x6B, 0x53, 0x68, 0x61, 0x72
)

const val SW_OK_HIGH: Byte = 0x90.toByte()
const val SW_OK_LOW: Byte = 0x00
val SW_OK = byteArrayOf(SW_OK_HIGH, SW_OK_LOW)
