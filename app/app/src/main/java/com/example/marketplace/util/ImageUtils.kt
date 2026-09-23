package com.example.marketplace.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

object ImageUtils {

    /**
     * Converte uma Uri da galeria em uma String Base64 comprimida.
     * Corrige a rotação EXIF, redimensiona proporcionalmente para não exceder [maxDimensao]
     * e comprime em formato JPEG com a [qualidade] informada (0 a 100).
     */
    fun uriParaBase64(
        context: Context,
        uri: Uri,
        maxDimensao: Int = 600,
        qualidade: Int = 70
    ): String? {
        return try {
            val contentResolver = context.contentResolver

            // 1. Decodifica dimensões originais e bitmap
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val bitmapOriginal = BitmapFactory.decodeStream(inputStream)
            inputStream.close() ?: return null
            if (bitmapOriginal == null) return null

            // 2. Corrige orientação EXIF (fotos de câmera)
            val rotationDegrees = getExifOrientationDegrees(context, uri)
            val bitmapOrientado = if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(
                    bitmapOriginal,
                    0,
                    0,
                    bitmapOriginal.width,
                    bitmapOriginal.height,
                    matrix,
                    true
                )
            } else {
                bitmapOriginal
            }

            // 3. Redimensiona proporcionalmente mantendo aspect ratio
            val largura = bitmapOrientado.width
            val altura = bitmapOrientado.height
            val escala = if (largura > altura) {
                if (largura > maxDimensao) maxDimensao.toFloat() / largura else 1.0f
            } else {
                if (altura > maxDimensao) maxDimensao.toFloat() / altura else 1.0f
            }

            val bitmapFinal = if (escala < 1.0f) {
                Bitmap.createScaledBitmap(
                    bitmapOrientado,
                    (largura * escala).toInt(),
                    (altura * escala).toInt(),
                    true
                )
            } else {
                bitmapOrientado
            }

            // 4. Comprime em JPEG
            val outputStream = ByteArrayOutputStream()
            bitmapFinal.compress(Bitmap.CompressFormat.JPEG, qualidade, outputStream)
            val bytes = outputStream.toByteArray()

            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Converte uma String Base64 em [Bitmap] do Android.
     */
    fun base64ParaBitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun getExifOrientationDegrees(context: Context, uri: Uri): Float {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }
    }
}

/**
 * Componente Jetpack Compose reutilizável para exibir avatar de usuário a partir de Base64.
 * Se não houver foto, exibe as iniciais ou ícone padrão.
 */
@Composable
fun AvatarUsuario(
    fotoBase64: String?,
    nome: String = "",
    modifier: Modifier = Modifier.size(48.dp),
    onClick: (() -> Unit)? = null
) {
    val bitmap = remember(fotoBase64) {
        ImageUtils.base64ParaBitmap(fotoBase64)?.asImageBitmap()
    }

    val clickableModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }

    Box(
        modifier = clickableModifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Foto de perfil de $nome",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (nome.isNotBlank()) {
            val inicial = nome.trim().first().uppercaseChar().toString()
            Text(
                text = inicial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Foto de perfil",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Componente Jetpack Compose reutilizável para exibir fotos de produtos a partir de Base64.
 */
@Composable
fun ImagemProduto(
    fotoBase64: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Foto do produto",
    contentScale: ContentScale = ContentScale.Crop,
    placeholderBackground: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val bitmap = remember(fotoBase64) {
        ImageUtils.base64ParaBitmap(fotoBase64)?.asImageBitmap()
    }

    Box(
        modifier = modifier.background(placeholderBackground),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            Icon(
                imageVector = Icons.Default.ShoppingBag,
                contentDescription = contentDescription,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
