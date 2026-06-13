package com.utl.idgs903.angel.iasimplemanoscuerpo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MjpegStreamReader(private val urlString: String, private val onFrameReceived: (Bitmap) -> Unit) {
    private var isRunning = false
    private var thread: Thread? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        thread = thread {
            var connection: HttpURLConnection? = null
            var inputStream: InputStream? = null
            try {
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                inputStream = connection.inputStream
                
                var soiIndex = -1
                var eoiIndex = -1
                var buffer = ByteArray(0)
                val readBuffer = ByteArray(4096)
                
                while (isRunning) {
                    val bytesRead = inputStream.read(readBuffer)
                    if (bytesRead == -1) break
                    
                    val newBuffer = ByteArray(buffer.size + bytesRead)
                    System.arraycopy(buffer, 0, newBuffer, 0, buffer.size)
                    System.arraycopy(readBuffer, 0, newBuffer, buffer.size, bytesRead)
                    buffer = newBuffer
                    
                    // Buscar SOI (0xFF, 0xD8)
                    if (soiIndex == -1) {
                        for (i in 0 until buffer.size - 1) {
                            if (buffer[i] == 0xFF.toByte() && buffer[i+1] == 0xD8.toByte()) {
                                soiIndex = i
                                break
                            }
                        }
                    }
                    
                    // Buscar EOI (0xFF, 0xD9)
                    if (soiIndex != -1) {
                        for (i in soiIndex until buffer.size - 1) {
                            if (buffer[i] == 0xFF.toByte() && buffer[i+1] == 0xD9.toByte()) {
                                eoiIndex = i + 1 // Incluir D9
                                break
                            }
                        }
                    }
                    
                    if (soiIndex != -1 && eoiIndex != -1) {
                        // Tenemos un frame completo
                        val jpegBytes = ByteArray(eoiIndex - soiIndex + 1)
                        System.arraycopy(buffer, soiIndex, jpegBytes, 0, jpegBytes.size)
                        
                        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                        if (bitmap != null) {
                            onFrameReceived(bitmap)
                        }
                        
                        // Recortar el buffer para el próximo frame
                        val remainingBytes = buffer.size - (eoiIndex + 1)
                        val nextBuffer = ByteArray(remainingBytes)
                        System.arraycopy(buffer, eoiIndex + 1, nextBuffer, 0, remainingBytes)
                        buffer = nextBuffer
                        
                        soiIndex = -1
                        eoiIndex = -1
                    }
                }
            } catch (e: Exception) {
                Log.e("MjpegStreamReader", "Error reading stream", e)
            } finally {
                try { inputStream?.close() } catch (e: Exception) {}
                connection?.disconnect()
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        thread?.interrupt()
        thread = null
    }
}
